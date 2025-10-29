#!/usr/bin/env groovy

/**
 * Scans Infrastructure as Code (IaC) files for misconfigurations using Trivy.
 * This detects security issues in Kubernetes YAML, Helm charts, Dockerfiles, Terraform, etc.
 *
 * @param config A map containing the configuration for the scan.
 *               - targets (optional): List of directories/files to scan. Defaults to ['k8s/', 'helm-charts/', '.'].
 *               - severities (optional): CSV of severities (e.g., 'HIGH,CRITICAL'). Defaults to 'MEDIUM,HIGH,CRITICAL'.
 *               - failOnIssues (optional): Boolean to fail the pipeline. Defaults to true.
 *               - timeout (optional): Timeout for the scan command. Defaults to '10m'.
 *               - skipDirs (optional): List of directory paths to skip. Defaults to ['node_modules', 'venv', '.git'].
 */
def call(Map config = [:]) {
    // --- Configuration with Defaults ---
    def targets = config.targets ?: ['k8s/', 'helm-charts/', '.']
    def severities = config.severities ?: 'MEDIUM,HIGH,CRITICAL'
    def failBuild = config.failOnIssues != false // Defaults to true
    def timeout = config.timeout ?: '10m'
    def exitCode = failBuild ? 1 : 0
    def skipDirs = config.skipDirs ?: ['node_modules', 'venv', '.git', 'frontend2/frontend/node_modules']
    
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

        // --- 2. Run IaC scans sequentially for all targets ---
        echo "🔒 Running Trivy IaC (misconfiguration) scan..."
        echo "📋 Targets to scan: ${targets.join(', ')}"
        
        def isolatedCacheDir = "${env.WORKSPACE}/.trivy-cache-iac-${UUID.randomUUID()}"
        try {
            echo "--- Preparing isolated cache for IaC scan ---"
            sh "mkdir -p ${isolatedCacheDir}"
            sh "cp -R ${persistentCacheDir}/* ${isolatedCacheDir}/"

            // Scan all targets in a single Trivy run (much faster!)
            echo "--- Scanning for IaC misconfigurations (${severities}) ---"
            echo "📋 This will scan:"
            echo "   - Kubernetes YAML files"
            echo "   - Helm charts"
            echo "   - Dockerfiles"
            echo "   - Terraform files"
            
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
                    --scanners misconfig \\
                    --timeout ${timeout} \\
                    /src/.
            """
            echo "✅ IaC scan completed. No critical misconfigurations found."
        } catch (e) {
            echo "❌ IaC scan failed or misconfigurations were found!"
            echo "💡 Review Kubernetes manifests, Helm charts, and Dockerfiles for security issues."
            throw e
        } finally {
            echo "--- Cleaning up isolated cache for IaC scan ---"
            sh "rm -rf ${isolatedCacheDir}"
        }
        
        echo "🎉 IaC scan completed successfully!"
    }
}

