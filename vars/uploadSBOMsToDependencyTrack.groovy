#!/usr/bin/env groovy

/**
 * Uploads SBOM (Software Bill of Materials) files to Dependency-Track.
 * 
 * This library searches for SBOM files in the specified directory and uploads them
 * to a Dependency-Track server using the Jenkins Dependency-Track plugin.
 * 
 * PREREQUISITES:
 * - Dependency-Track Jenkins plugin must be installed
 * - Dependency-Track connection must be configured in Jenkins (Manage Jenkins -> Configure System)
 * - API key must be added to Jenkins credentials
 * 
 * @param config A map containing the configuration for SBOM upload.
 *               - outputDir (required): Directory containing SBOM files to upload.
 *               - projectName (required): Project name in Dependency-Track.
 *               - projectVersion (required): Project version (typically git commit SHA or semantic version).
 *               - autoCreate (optional): Auto-create project if it doesn't exist. Defaults to true.
 *               - synchronous (optional): Wait for upload processing to complete. Defaults to true.
 * 
 * @example
 * uploadSBOMsToDependencyTrack(
 *     outputDir: 'sbom-reports',
 *     projectName: 'todo-app',
 *     projectVersion: 'abc1234',
 *     autoCreate: true
 * )
 */
def call(Map config) {
    // Validate required parameters
    if (!config.outputDir) {
        error("❌ outputDir is required! Specify the directory containing SBOM files.")
    }
    if (!config.projectName) {
        error("❌ projectName is required! Specify the Dependency-Track project name.")
    }
    if (!config.projectVersion) {
        error("❌ projectVersion is required! Specify the project version (e.g., git SHA or semantic version).")
    }
    
    // Configuration with defaults
    def outputDir = config.outputDir
    def projectName = config.projectName
    def projectVersion = config.projectVersion
    def autoCreate = config.autoCreate != false  // Defaults to true
    def synchronous = config.synchronous != false  // Defaults to true
    
    echo "📤 Uploading SBOMs to Dependency-Track..."
    echo "   Project Name: ${projectName}"
    echo "   Project Version: ${projectVersion}"
    echo "   Output Directory: ${outputDir}"
    echo "   Auto-create Project: ${autoCreate}"
    echo "   Synchronous Upload: ${synchronous}"
    
    try {
        echo "🔍 Searching for SBOM files in ${outputDir}..."
        
        // Find all SBOM files in the directory
        def sbomFiles = sh(
            script: "find ${outputDir} -name '*.sbom.json' -type f 2>/dev/null || echo ''",
            returnStdout: true
        ).trim()
        
        if (!sbomFiles || sbomFiles == '') {
            echo "⚠️ No SBOM files found in ${outputDir}"
            echo "ℹ️ Skipping Dependency-Track upload"
            return
        }
        
        def sbomFilesList = sbomFiles.split('\n').findAll { it.trim() != '' }
        echo "📋 Found ${sbomFilesList.size()} SBOM file(s) to upload"
        
        // Upload each SBOM file
        def uploadCount = 0
        def failCount = 0
        
        sbomFilesList.each { sbomFile ->
            if (sbomFile && sbomFile.trim() != '') {
                def fileName = sbomFile.tokenize('/').last()
                echo "📤 Uploading: ${fileName}"
                
                try {
                    // Use Dependency-Track Jenkins Plugin API
                    // Documentation: https://plugins.jenkins.io/dependency-track/
                    dependencyTrackPublisher(
                        artifact: sbomFile,
                        projectName: projectName,
                        projectVersion: projectVersion,
                        synchronous: synchronous,
                        autoCreateProjects: autoCreate
                    )
                    
                    uploadCount++
                    echo "✅ Successfully uploaded: ${fileName}"
                    
                } catch (Exception e) {
                    failCount++
                    echo "⚠️ Failed to upload ${fileName}"
                    echo "   Error: ${e.getMessage()}"
                    
                    // Log detailed error for debugging
                    if (e.getMessage().contains('No credentials')) {
                        echo "   💡 Hint: Check Jenkins credentials configuration"
                    } else if (e.getMessage().contains('connection')) {
                        echo "   💡 Hint: Check Dependency-Track URL in Jenkins configuration"
                    } else if (e.getMessage().contains('plugin')) {
                        echo "   💡 Hint: Ensure Dependency-Track plugin is installed"
                    }
                    
                    // Continue with other files even if one fails
                }
            }
        }
        
        // Summary
        echo ""
        echo "📊 Upload Summary:"
        echo "   ✅ Successful uploads: ${uploadCount}"
        if (failCount > 0) {
            echo "   ⚠️ Failed uploads: ${failCount}"
        }
        echo "   📁 Total files processed: ${sbomFilesList.size()}"
        
        if (uploadCount > 0) {
            echo ""
            echo "✅ Dependency-Track upload completed!"
            echo "🔗 View results at: Dependency-Track Web UI -> Projects -> ${projectName} (${projectVersion})"
            echo "   - Components: View all dependencies"
            echo "   - Vulnerabilities: Check security issues"
            echo "   - Risk Score: See overall project risk"
        } else if (failCount > 0) {
            echo ""
            echo "⚠️ All uploads failed. Please check configuration:"
            echo "   1. Manage Jenkins -> Configure System -> Dependency-Track"
            echo "   2. Verify Dependency-Track URL is correct"
            echo "   3. Verify API key is valid"
            echo "   4. Test connection in Jenkins configuration"
        }
        
    } catch (Exception e) {
        echo ""
        echo "❌ Error during Dependency-Track upload: ${e.getMessage()}"
        echo ""
        echo "🔧 Troubleshooting Steps:"
        echo "   1. Install Dependency-Track plugin:"
        echo "      Manage Jenkins -> Plugins -> Available -> Search 'Dependency-Track'"
        echo ""
        echo "   2. Configure connection:"
        echo "      Manage Jenkins -> Configure System -> Dependency-Track section"
        echo "      - Dependency-Track URL: http://dtrack.example.com"
        echo "      - API Key: Add as Jenkins credential (Secret text)"
        echo ""
        echo "   3. Test connection:"
        echo "      Click 'Test Connection' button in configuration"
        echo ""
        echo "   4. Verify SBOM files exist:"
        echo "      Check that ${outputDir} contains *.sbom.json files"
        echo ""
        
        // Don't fail the build - SBOM upload is optional
        echo "⚠️ Build will continue despite upload failure (SBOM upload is optional)"
    }
}
