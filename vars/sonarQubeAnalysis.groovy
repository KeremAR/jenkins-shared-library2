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
        echo "📝 Creating sonar-project.properties file with coverage paths..."
        // Find all coverage.xml files in the reports directory and create a comma-separated string
        def coveragePaths = findFiles(glob: 'reports/**/coverage.xml').collect { it.path }.join(',')
        echo "Found coverage reports at: ${coveragePaths}"

        writeFile file: 'sonar-project.properties', text: """
            sonar.projectKey=${config.projectKey}
            sonar.sources=.
            sonar.exclusions=**/node_modules/**,**/test/**
            # Tell SonarQube where to find the coverage reports
            sonar.python.coverage.reportPaths=${coveragePaths}
        """
        
        echo "🔎 Preparing SonarQube analysis environment and waiting for Quality Gate..."
        // Timeout the whole analysis and wait step after 15 minutes
        timeout(time: 15, unit: 'MINUTES') {
            // This uses the SonarQube Scanner plugin configured in Jenkins "Manage Jenkins -> System"
            // and the tool configured in "Manage Jenkins -> Tools".
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

