#!/usr/bin/env groovy

def call(Map config) {
    // 1. Validate required parameters
    if (!config.scannerName) {
        error("Missing required parameter. 'scannerName' (the name of the SonarQube Scanner tool in Jenkins) must be provided.")
    }
    if (!config.projectKey) {
        error("Missing required parameter. 'projectKey' for SonarQube must be provided.")
    }

    try {
        echo "📝 Creating sonar-project.properties file..."
        // No coverage paths are included in this version.
        writeFile file: 'sonar-project.properties', text: """
            sonar.projectKey=${config.projectKey}
            sonar.sources=.
            sonar.exclusions=**/node_modules/**,**/test/**
        """
        
        echo "🔎 Preparing SonarQube analysis environment and waiting for Quality Gate..."
        // Timeout the whole analysis and wait step after 15 minutes
        timeout(time: 15, unit: 'MINUTES') {
            withSonarQubeEnv(config.serverName ?: 'sonarqube') {
                def scannerHome = tool config.scannerName
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

