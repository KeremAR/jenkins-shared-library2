#!/usr/bin/env groovy

def call(Map config) {
    // 1. Gerekli parametrelerin varlığını kontrol et
    if (!config.projectKey || !config.sonarHostUrl || !config.sonarToken) {
        error("Missing required parameter. 'projectKey', 'sonarHostUrl', and 'sonarToken' must be provided.")
    }

    container('docker') {
        echo "🔎 Running SonarQube analysis using official Docker image..."
        try {
            // 2. Token'ı güvenli bir şekilde withEnv bloğuna al
            withEnv(["SONAR_SECRET=${config.sonarToken}"]) {
                
                // 3. sonar-project.properties dosyasını workspace'e yaz.
                // Bu, komut satırını temiz tutar.
                writeFile file: 'sonar-project.properties', text: """
                    sonar.projectKey=${config.projectKey}
                    sonar.sources=.
                    sonar.host.url=${config.sonarHostUrl}
                """

                // 4. Resmi sonar-scanner imajını çalıştır.
                // -v ile mevcut projenin kodlarını container içine bağlıyoruz.
                // -e ile token'ı güvenli bir şekilde ortam değişkeni olarak veriyoruz.
                // sonar-scanner, sonar-project.properties dosyasını otomatik olarak bulup okuyacaktır.
                sh '''
                    docker run --rm \\
                        -v ${WORKSPACE}:/usr/src \\
                        -e SONAR_TOKEN=${SONAR_SECRET} \\
                        sonarsource/sonar-scanner-cli
                '''
            }
            echo "✅ SonarQube analysis submitted successfully."
        } catch (e) {
            echo "❌ SonarQube analysis failed!"
            error("Error during SonarQube analysis: ${e.toString()}")
        }
    }
}