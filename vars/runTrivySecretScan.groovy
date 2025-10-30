#!/usr/bin/env groovy

/**
 * Scans the repository for exposed secrets using Trivy.
 * This is a pre-build security check (Shift-Left Security).
 *
 * @param config A map containing the configuration for the scan.
 *               - target (optional): Path to scan. Defaults to '.' (current directory).
 *               - severities (optional): CSV of severities (e.g., 'HIGH,CRITICAL'). Defaults to 'HIGH,CRITICAL'.
 *               - failOnSecrets (optional): Boolean to fail the pipeline if secrets are found. Defaults to true.
 *               - timeout (optional): Timeout for the scan command. Defaults to '10m'.
 *               - skipDirs (optional): List of directory paths to skip. Defaults to an empty list.
 */
def call(Map config = [:]) {
    // --- Configuration with Defaults ---
    def target = config.target ?: '.'
    def severities = config.severities ?: 'HIGH,CRITICAL'
    def failBuild = config.failOnSecrets != false // Defaults to true
    def timeout = config.timeout ?: '10m'
    def exitCode = failBuild ? 1 : 0
    def skipDirs = config.skipDirs ?: []

    // Construct the --skip-dirs flags string from the list of directories
    def skipDirsFlags = skipDirs.collect { dir -> "--skip-dirs ${dir}" }.join(' ')
    
    // Define the persistent cache directory (mounted from PVC)
    def persistentCacheDir = "/home/jenkins/.trivy-cache"

    container('docker') {
        echo "🔐 Running Trivy secret scan on repository..."
        
        // Create an isolated copy of the persistent DB for this scan
        def isolatedCacheDir = "${env.WORKSPACE}/.trivy-cache-isolated-secrets-${UUID.randomUUID()}"
        
        try {
            echo "--- Preparing isolated cache ---"
            sh "mkdir -p ${isolatedCacheDir}"
            sh "cp -R ${persistentCacheDir}/* ${isolatedCacheDir}/ || true"

            echo "--- Scanning ${target} for exposed secrets ---"
            sh """
                docker run --rm \\
                    -v ${env.WORKSPACE}:/src \\
                    -v ${isolatedCacheDir}:/root/.cache/trivy \\
                    -v ${env.WORKSPACE}/.trivyignore:/.trivyignore \\
                    aquasec/trivy:latest \\
                    fs \\
                    --skip-db-update \\
                    ${skipDirsFlags} \\
                    --exit-code ${exitCode} \\
                    --severity '${severities}' \\
                    --scanners secret \\
                    --timeout ${timeout} \\
                    /src/${target}
            """
            
            echo "✅ No secrets found (or below severity threshold: ${severities})."
        } catch (e) {
            echo "❌ Secret scan failed! Exposed secrets detected in the repository!"
            echo "🚨 CRITICAL: Please remove secrets immediately and rotate credentials!"
            throw e
        } finally {
            echo "--- Cleaning up isolated cache ---"
            sh "rm -rf ${isolatedCacheDir}"
        }
    }
}

