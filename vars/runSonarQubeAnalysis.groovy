#!/usr/bin/env groovy

def call(Map config) {
    def projectKey = config.projectKey
    def sonarHostUrl = config.sonarHostUrl
    def sonarToken = config.sonarToken

    container('docker') {
        echo "🔎 Running SonarQube analysis..."
        try {
            // Using the official sonar-scanner-cli docker image
            // It requires access to the full source code, so we mount the workspace
            sh """
                docker run --rm \\
                    -v \${env.WORKSPACE}:/usr/src \\
                    sonarsource/sonar-scanner-cli \\
                    -Dsonar.projectKey=${projectKey} \\
                    -Dsonar.sources=. \\
                    -Dsonar.host.url=${sonarHostUrl} \\
                    -Dsonar.login=${sonarToken}
            """
            echo "✅ SonarQube analysis submitted successfully."
        } catch (e) {
            echo "❌ SonarQube analysis failed!"
            throw e
        }
    }
}
