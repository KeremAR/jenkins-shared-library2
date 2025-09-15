#!/usr/bin/env groovy

/**
 * Scans a list of Docker images for vulnerabilities using Trivy.
 *
 * @param config A map containing the configuration for the scan.
 *               - images (required): A list of Docker image names to scan.
 *               - severities (optional): A comma-separated string of severities to report (e.g., 'HIGH,CRITICAL'). Defaults to 'HIGH,CRITICAL'.
 *               - failOnVulnerabilities (optional): A boolean indicating whether to fail the pipeline if vulnerabilities are found. Defaults to true.
 *               - timeout (optional): The timeout for the scan command. Defaults to '15m'.
 *               - skipDirs (optional): A list of directory paths to skip during the scan (e.g., ['/app/node_modules']). Defaults to an empty list.
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

    // Construct the --skip-dirs flags string from the list of directories
    def skipDirsFlags = skipDirs.collect { dir -> "--skip-dirs ${dir}" }.join(' ')

    container('docker') {
        // --- 1. Pre-download/update the DB to a shared location ---
        echo "🔄 Updating Trivy vulnerability database to a shared cache..."
        def sharedCacheDir = ".trivy-cache/shared"
        try {
            // Ensure the shared cache directory exists on the agent before mounting
            sh "mkdir -p ${sharedCacheDir}"
            // Use single quotes to prevent Groovy interpolation issues.
            sh '''
                docker run --rm \\
                    -v ${WORKSPACE}/''' + sharedCacheDir + ''':/root/.cache/trivy \\
                    aquasec/trivy:latest \\
                    image --download-db-only --quiet
            '''
            echo "✅ Trivy database is up to date."
        } catch(e) {
            echo "⚠️ Could not update Trivy DB. Scans will proceed but may use an older DB if one exists."
            // We don't fail the build here, as Trivy can often proceed with a slightly older DB.
        }


        // --- 2. Run scans sequentially using the shared DB ---
        echo "🛡️ Running Trivy vulnerability scan for images sequentially..."
        images.each { imageName ->
            try {
                echo "--- Scanning ${imageName} for ${severities} vulnerabilities ---"
                // Use single quotes to prevent Groovy interpolation issues.
                // Mount the SHARED cache directory and skip the DB update on each scan.
                sh '''
                    docker run --rm \\
                        -v /var/run/docker.sock:/var/run/docker.sock \\
                        -v ${WORKSPACE}/''' + sharedCacheDir + ''':/root/.cache/trivy \\
                        aquasec/trivy:latest \\
                        image \\
                        --skip-db-update \\
                        ''' + skipDirsFlags + ''' \\
                        --exit-code ''' + exitCode + ''' \\
                        --severity ''' + severities + ''' \\
                        --scanners vuln \\
                        --timeout ''' + timeout + ''' \\
                        ''' + imageName + '''
                '''
                echo "✅ Trivy scan completed for ${imageName}. No vulnerabilities found at specified severity level."
            } catch (e) {
                echo "❌ Trivy scan failed for ${imageName} or vulnerabilities were found!"
                // Re-throw the exception to fail the pipeline stage
                throw e
            }
        }
        echo "🎉 All Trivy scans completed!"
    }
}
