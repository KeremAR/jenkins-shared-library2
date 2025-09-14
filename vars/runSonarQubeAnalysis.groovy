#!/usr/bin/env groovy

def call(Map config) {
    def projectKey = config.projectKey
    def sonarHostUrl = config.sonarHostUrl
    def sonarToken = config.sonarToken

    container('docker') {
        echo "🔎 Running SonarQube analysis..."
        try {
            // Jenkins passes the credential content to the 'sonarToken' variable.
            // We then expose this variable to the 'sh' step's environment using withEnv.
            // This is the secure way to handle secrets and avoids shell injection or "bad substitution" errors.
            withEnv(["SONAR_SECRET=${sonarToken}"]) {
                // We use single quotes for the sh step to prevent Groovy from interpolating variables.
                // The sonar-scanner-cli automatically picks up the SONAR_TOKEN environment variable.
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
            echo "❌ SonarQube analysis failed!"
            throw e
        }
    }
}
