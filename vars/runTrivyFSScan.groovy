#!/usr/bin/env groovy

/**
 * Scans filesystem for dependency vulnerabilities using Trivy.
 * This detects CVEs in dependencies from requirements.txt, package.json, go.mod, etc.
 * 
 * NOTE: This is a filesystem scan, NOT a Docker image scan.
 * It's much faster because it only scans dependency manifests without building images.
 *
 * @param config A map containing the configuration for the scan.
 *               - target (optional): Directory to scan. Defaults to '.'.
 *               - severities (optional): CSV of severities (e.g., 'HIGH,CRITICAL'). Defaults to 'HIGH,CRITICAL'.
 *               - failOnVulnerabilities (optional): Boolean to fail the pipeline. Defaults to true.
 *               - timeout (optional): Timeout for the scan command. Defaults to '15m'.
 *               - skipDirs (optional): List of directory paths to skip. Defaults to ['node_modules', 'venv', '.git'].
 */
def call(Map config = [:]) {
    // --- Configuration with Defaults ---
    def target = config.target ?: '.'
    def severities = config.severities ?: 'HIGH,CRITICAL'
    def failBuild = config.failOnVulnerabilities != false // Defaults to true
    def timeout = config.timeout ?: '15m'
    def exitCode = failBuild ? 1 : 0
    def skipDirs = config.skipDirs ?: ['node_modules', 'venv', '.git', 'frontend2/frontend/node_modules', '__pycache__']

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

        // --- 2. Run filesystem dependency scan ---
        echo "📦 Running Trivy filesystem scan for dependency vulnerabilities..."
        def isolatedCacheDir = "${env.WORKSPACE}/.trivy-cache-fs-${UUID.randomUUID()}"
        try {
            echo "--- Preparing isolated cache for filesystem scan ---"
            sh "mkdir -p ${isolatedCacheDir}"
            sh "cp -R ${persistentCacheDir}/* ${isolatedCacheDir}/"

            echo "--- Scanning ${target} for dependency vulnerabilities (${severities}) ---"
            echo "📋 This will scan:"
            echo "   - requirements.txt (Python)"
            echo "   - package.json / package-lock.json (Node.js)"
            echo "   - go.mod / go.sum (Go)"
            echo "   - And other dependency manifests..."
            
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
                    --scanners vuln \\
                    --timeout ${timeout} \\
                    /src/${target}
            """
            echo "✅ Filesystem scan completed. No vulnerabilities found at specified severity level."
        } catch (e) {
            echo "❌ Filesystem scan failed or vulnerabilities were found!"
            echo "💡 Check dependency files (requirements.txt, package.json) for CVEs."
            echo "🔧 Consider updating vulnerable packages or adding exceptions to .trivyignore"
            throw e
        } finally {
            echo "--- Cleaning up isolated cache for filesystem scan ---"
            sh "rm -rf ${isolatedCacheDir}"
        }
        
        echo "🎉 Filesystem dependency scan completed successfully!"
    }
}

