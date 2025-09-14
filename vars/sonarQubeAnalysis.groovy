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
        writeFile file: 'sonar-project.properties', text: """
            sonar.projectKey=${config.projectKey}
            sonar.sources=.
            sonar.exclusions=**/node_modules/**,**/test/**
        """
        
        echo "🔎 Preparing SonarQube analysis environment..."
        // This uses the SonarQube Scanner plugin configured in Jenkins "Manage Jenkins -> System"
        // and the tool configured in "Manage Jenkins -> Tools".
        withSonarQubeEnv(config.serverName ?: 'sonarqube') {
            def scannerHome = tool config.scannerName
            sh "${scannerHome}/bin/sonar-scanner"
        }
        echo "✅ SonarQube analysis submitted successfully."

    } catch (e) {
        echo "❌ SonarQube analysis failed!"
        error("Error during SonarQube analysis: ${e.toString()}")
    }
}
