#!/usr/bin/env groovy

/**
 * Generates Software Bill of Materials (SBOM) for Docker images using Trivy.
 * SBOM provides a complete inventory of all components in the software supply chain.
 *
 * @param config A map containing the configuration for SBOM generation.
 *               - images (required): A list of Docker image names to generate SBOMs for.
 *               - format (optional): SBOM format. Options: 'cyclonedx', 'spdx', 'spdx-json', 'json'. Defaults to 'cyclonedx'.
 *               - outputDir (optional): Directory to save SBOM files. Defaults to 'sbom-reports'.
 *               - skipDirs (optional): List of directory paths to skip. Defaults to an empty list.
 */
def call(Map config) {
    // --- Configuration with Defaults ---
    if (!config.images) {
        error("Missing required parameter: 'images' must be provided with a list of Docker image names.")
    }
    def images = config.images
    def format = config.format ?: 'cyclonedx'
    def outputDir = config.outputDir ?: 'sbom-reports'
    def skipDirs = config.skipDirs ?: []

    // Filter out ':latest' tags from the image list
    def imagesToProcess = images.findAll { it -> it instanceof String && !it.endsWith(':latest') }
    if (imagesToProcess.isEmpty()) {
        echo "No images to generate SBOM for after filtering ':latest' tags. Skipping."
        return
    }
    echo "Generating SBOM for images: ${imagesToProcess}"

    // Construct the --skip-dirs flags string
    def skipDirsFlags = skipDirs.collect { dir -> "--skip-dirs ${dir}" }.join(' ')
    
    // Define the persistent cache directory
    def persistentCacheDir = "/home/jenkins/.trivy-cache"

    container('docker') {
        echo "📦 Generating Software Bill of Materials (SBOM) for images..."
        
        // Create output directory
        sh "mkdir -p ${outputDir}"
        
        // Generate SBOM for each image in parallel
        def parallelSBOM = [:]
        imagesToProcess.each { imageName ->
            parallelSBOM["SBOM ${imageName}"] = {
                stage("SBOM ${imageName}") {
                    def isolatedCacheDir = "${env.WORKSPACE}/.trivy-cache-isolated-sbom-${UUID.randomUUID()}"
                    
                    // Sanitize image name for filename (replace special chars with -)
                    def sanitizedName = imageName.replaceAll('[^a-zA-Z0-9_-]', '-')
                    def outputFile = "${outputDir}/${sanitizedName}.sbom.json"
                    
                    try {
                        echo "--- Preparing isolated cache for ${imageName} ---"
                        sh "mkdir -p ${isolatedCacheDir}"
                        sh "cp -R ${persistentCacheDir}/* ${isolatedCacheDir}/"

                        echo "--- Generating ${format.toUpperCase()} SBOM for ${imageName} ---"
                        sh """
                            docker run --rm \\
                                -v /var/run/docker.sock:/var/run/docker.sock \\
                                -v ${isolatedCacheDir}:/root/.cache/trivy \\
                                -v ${env.WORKSPACE}/${outputDir}:/output \\
                                aquasec/trivy:latest \\
                                image \\
                                --skip-db-update \\
                                ${skipDirsFlags} \\
                                --format ${format} \\
                                --output /output/${sanitizedName}.sbom.json \\
                                '${imageName}'
                        """
                        
                        echo "✅ SBOM generated: ${outputFile}"
                    } catch (e) {
                        echo "⚠️ Warning: Failed to generate SBOM for ${imageName}"
                        echo "Error: ${e.getMessage()}"
                        // Don't fail the pipeline, SBOM generation is informational
                    } finally {
                        echo "--- Cleaning up isolated cache for ${imageName} ---"
                        sh "rm -rf ${isolatedCacheDir}"
                    }
                }
            }
        }
        parallel parallelSBOM
        
        // Fix permissions on SBOM directory (Docker creates files as root)
        echo "🔧 Fixing SBOM file permissions for Jenkins cleanup..."
        sh "chmod -R 777 ${env.WORKSPACE}/${outputDir} || true"
        
        // Archive SBOM artifacts in Jenkins
        try {
            echo "📁 Archiving SBOM artifacts..."
            archiveArtifacts artifacts: "${outputDir}/*.sbom.json", allowEmptyArchive: true
            echo "✅ SBOM artifacts archived successfully!"
        } catch (e) {
            echo "⚠️ Warning: Could not archive SBOM artifacts"
            echo "Error: ${e.getMessage()}"
        }
        
        echo "🎉 SBOM generation completed for ${imagesToProcess.size()} image(s)!"
    }
}

