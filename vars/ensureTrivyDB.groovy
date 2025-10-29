#!/usr/bin/env groovy

/**
 * Ensures Trivy vulnerability database is available in persistent cache.
 * This is a utility function to avoid code duplication across Trivy scan libraries.
 * 
 * The database is stored in a persistent PVC (/home/jenkins/.trivy-cache) and
 * only needs to be updated periodically, not on every scan.
 * 
 * @param config A map containing the configuration.
 *               - forceUpdate (optional): Force database update. Defaults to false.
 */
def call(Map config = [:]) {
    def forceUpdate = config.forceUpdate ?: false
    def persistentCacheDir = "/home/jenkins/.trivy-cache"
    
    container('docker') {
        // Check if DB already exists (skip unnecessary updates)
        def dbExists = sh(
            script: "[ -d ${persistentCacheDir}/db ] && echo 'true' || echo 'false'",
            returnStdout: true
        ).trim()
        
        if (dbExists == 'true' && !forceUpdate) {
            echo "✅ Trivy database already exists in persistent cache. Skipping update."
            echo "💡 To force update, set forceUpdate: true"
            return
        }
        
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
            echo "Error: ${e.getMessage()}"
        }
    }
}

