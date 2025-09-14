#!/usr/bin/env groovy

def call(Map config) {
    def projectKey = config.projectKey
    def sonarHostUrl = config.sonarHostUrl
    def sonarToken = config.sonarToken

    container('docker') {
        echo "🔎 Running SonarQube analysis..."
        try {
            // 1. Check if the token variable is null or empty
            if (sonarToken == null || sonarToken.trim().isEmpty()) {
                // Stop the pipeline with a clear error message
                error("SonarQube token is missing or empty. Please check the 'sonarqube-token-id' credential in Jenkins.")
            }
            echo "DEBUG: Successfully received a SonarQube token."

            // 2. Use withEnv to securely handle the secret
            withEnv(["SONAR_SECRET=${sonarToken}"]) {
                sh '''
                    docker run --rm \\
                        -v ${WORKSPACE}:/usr/src \\
                        -e SONAR_TOKEN=${SONAR_SECRET} \\
                        sonarsource/sonar-scanner-cli \\
                        -Dsonar.projectKey=''' + projectKey + ''' \\
                        -Dsonar.sources=. \\
                        -Dsonar.host.url=''' + sonarHostUrl
                '''
            }
            echo "✅ SonarQube analysis submitted successfully."
        } catch (e) {
            echo "❌ SonarQube analysis failed! See the error below."
            // 3. Make the error message visible and stop the pipeline
            error("Error during SonarQube analysis: ${e.toString()}")
        }
    }
}
