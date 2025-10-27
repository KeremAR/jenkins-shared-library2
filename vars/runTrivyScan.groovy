#!/usr/bin/env groovy

/**
 * Scans a list of Docker images for vulnerabilities using Trivy in parallel.
 * This script uses a persistent cache to speed up vulnerability DB downloads.
 *
 * @param config A map containing the configuration for the scan.
 *               - images (required): A list of Docker image names to scan.
 *               - severities (optional): CSV of severities (e.g., 'HIGH,CRITICAL'). Defaults to 'HIGH,CRITICAL'.
 *               - failOnVulnerabilities (optional): Boolean to fail the pipeline. Defaults to true.
 *               - timeout (optional): Timeout for the scan command. Defaults to '15m'.
 *               - skipDirs (optional): List of directory paths to skip inside the container. Defaults to an empty list.
 */
def call(Map config) {
    // --- Configuration with Defaults ---
    if (!config.images) {
        error("Missing required parameter: 'images' must be provided with a list of Docker image names to scan.")
    }
    def images = config.images
    def severities = config.severities ?: 'HIGH,CRITICAL'
    def failBuild = config.failOnVulnerabilities != false // Defaults to true
    def timeout = config.timeout ?: '15m'
    def exitCode = failBuild ? 1 : 0
    def skipDirs = config.skipDirs ?: []

    // --- Filter out ':latest' tags from the image list ---
    def imagesToScan = images.findAll { it -> it instanceof String && !it.endsWith(':latest') }
    if (imagesToScan.isEmpty()) {
        echo "No images to scan after filtering for ':latest' tags. Skipping."
        return
    }
    echo "Filtered images to scan: ${imagesToScan}"

    // Construct the --skip-dirs flags string from the list of directories
    def skipDirsFlags = skipDirs.collect { dir -> "--skip-dirs ${dir}" }.join(' ')
    
    // Define the persistent cache directory mounted into the pod from Utils.groovy
    def persistentCacheDir = "/home/jenkins/.trivy-cache"

    container('docker') {
        // --- 1. Pre-download/update the DB to the persistent location ---
        echo "🔄 Updating Trivy vulnerability database in persistent cache..."
        try {
            sh "mkdir -p ${persistentCacheDir}/db && mkdir -p ${persistentCacheDir}/java-db"
            
            sh """
                docker run --rm \\
                    -v ${persistentCacheDir}:/root/.cache/trivy \\
                    aquasec/trivy:latest \\
                    image --download-db-only --quiet
            """
            echo "✅ Trivy database is up to date."
        } catch(e) {
            echo "⚠️ Could not update Trivy DB. Scans will proceed but may use an older DB if one exists."
        }

        // --- 2. Run scans in parallel using isolated copies of the persistent DB ---
        echo "🛡️ Running Trivy vulnerability scan for images in parallel..."
        def parallelScans = [:]
        imagesToScan.each { imageName ->
            parallelScans["Scan ${imageName}"] = {
                stage("Scan ${imageName}") {
                    def isolatedCacheDir = "${env.WORKSPACE}/.trivy-cache-isolated-${UUID.randomUUID()}"
                    try {
                        echo "--- Preparing isolated cache for ${imageName} ---"
                        sh "mkdir -p ${isolatedCacheDir}"
                        sh "cp -R ${persistentCacheDir}/* ${isolatedCacheDir}/"

                        echo "--- Scanning ${imageName} for ${severities} vulnerabilities ---"
                        sh """
                            docker run --rm \\
                                -v /var/run/docker.sock:/var/run/docker.sock \\
                                -v ${isolatedCacheDir}:/root/.cache/trivy \\
                                -v ${env.WORKSPACE}/.trivyignore:/.trivyignore \\
                                aquasec/trivy:latest \\
                                image \\
                                --skip-db-update \\
                                ${skipDirsFlags} \\
                                --exit-code ${exitCode} \\
                                --severity '${severities}' \\
                                --scanners vuln \\
                                --timeout ${timeout} \\
                                '${imageName}'
                        """
                        echo "✅ Trivy scan completed for ${imageName}. No vulnerabilities found at specified severity level."
                    } catch (e) {
                        echo "❌ Trivy scan failed for ${imageName} or vulnerabilities were found!"
                        throw e
                    } finally {
                        echo "--- Cleaning up isolated cache for ${imageName} ---"
                        sh "rm -rf ${isolatedCacheDir}"
                    }
                }
            }
        }
        parallel parallelScans
        
        echo "🎉 All Trivy scans completed!"
    }
}
