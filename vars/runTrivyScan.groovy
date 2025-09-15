#!/usr/bin/env groovy

/**
 * Scans a list of Docker images for vulnerabilities using Trivy.
 *
 * @param config A map containing the configuration for the scan.
 *               - images (required): A list of Docker image names to scan.
 *               - severities (optional): A comma-separated string of severities to report (e.g., 'HIGH,CRITICAL'). Defaults to 'HIGH,CRITICAL'.
 *               - failOnVulnerabilities (optional): A boolean indicating whether to fail the pipeline if vulnerabilities are found. Defaults to true.
 *               - timeout (optional): The timeout for the scan command. Defaults to '15m'.
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

    container('docker') {
        echo "🛡️ Running Trivy vulnerability scan for images in parallel..."
        def parallelScans = [:]
        images.each { imageName ->
            // Sanitize image name for use as a valid Jenkins parallel stage name and directory name
            def stageName = imageName.replaceAll(/[^a-zA-Z0-9-]/, '_')

            // Each parallel scan gets its own unique cache directory to prevent race conditions
            def cacheDir = ".trivy-cache/${stageName}"

            parallelScans["Scan_${stageName}"] = {
                try {
                    // Ensure the unique cache directory exists on the agent before mounting
                    sh "mkdir -p ${cacheDir}"

                    echo "Scanning ${imageName} for ${severities} vulnerabilities..."
                    // Use single quotes to prevent Groovy interpolation issues.
                    // Mount a unique cache directory for each scan.
                    sh '''
                        docker run --rm \\
                            -v /var/run/docker.sock:/var/run/docker.sock \\
                            -v ${WORKSPACE}/''' + cacheDir + ''':/root/.cache/trivy \\
                            aquasec/trivy:latest \\
                            image \\
                            --exit-code ''' + exitCode + ''' \\
                            --severity ''' + severities + ''' \\
                            --scanners vuln \\
                            --timeout ''' + timeout + ''' \\
                            ''' + imageName + '''
                    '''
                    echo "✅ Trivy scan completed for ${imageName}. No vulnerabilities found at specified severity level."
                } catch (e) {
                    echo "❌ Trivy scan failed for ${imageName} or vulnerabilities were found!"
                    throw e
                }
            }
        }
        parallel parallelScans
        echo "🎉 All Trivy scans completed!"
    }
}
