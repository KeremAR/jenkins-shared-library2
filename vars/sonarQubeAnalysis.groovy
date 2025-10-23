#!/usr/bin/env groovy

/**
 * Performs SonarQube static code analysis and waits for Quality Gate result.
 *
 * @param config A map containing the configuration for the SonarQube analysis.
 *               - scannerName (required): The name of the SonarQube Scanner tool configured in Jenkins.
 *               - projectKey (required): The unique project key for SonarQube analysis.
 *               - serverName (optional): The name of the SonarQube server configured in Jenkins (default: 'sonarqube').
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.scannerName) {
        error("Missing required parameter. 'scannerName' (the name of the SonarQube Scanner tool in Jenkins) must be provided.")
    }
    if (!config.projectKey) {
        error("Missing required parameter. 'projectKey' for SonarQube must be provided.")
    }

    def scannerName = config.scannerName
    def projectKey = config.projectKey
    def serverName = config.serverName

    try {
        echo "📝 Creating sonar-project.properties file..."
        // No coverage paths are included in this version.
        writeFile file: 'sonar-project.properties', text: """
            sonar.projectKey=${projectKey}
            sonar.sources=.
            sonar.exclusions=**/node_modules/**,**/test/**
        """

        echo "🔎 Preparing SonarQube analysis environment and waiting for Quality Gate..."
        // Timeout the whole analysis and wait step after 15 minutes
        timeout(time: 15, unit: 'MINUTES') {
            withSonarQubeEnv(serverName ?: 'sonarqube') {
                def scannerHome = tool scannerName
                echo "Scanner home path: ${scannerHome}"
                sh "ls -la ${scannerHome} || true"
                sh "ls -la ${scannerHome}/bin || true"
                sh "${scannerHome}/bin/sonar-scanner"
            }

            // After analysis is submitted, this step pauses the pipeline
            // and waits for the webhook from SonarQube to report the Quality Gate status.
            waitForQualityGate abortPipeline: true
        }

        echo "✅ SonarQube Quality Gate passed!"

    } catch (e) {
        echo "❌ SonarQube analysis or Quality Gate failed!"
        error("Error: ${e.toString()}")
    }
}

