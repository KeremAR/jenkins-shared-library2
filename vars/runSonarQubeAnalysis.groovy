def call(Map config) {
    echo "Running SonarQube analysis with config: ${config}"
    
    // Validate required parameters
    if (!config.projectKey) {
        error "SonarQube project key is required"
    }
    if (!config.sonarHostUrl) {
        error "SonarQube host URL is required"
    }
    if (!config.sonarToken) {
        error "SonarQube token is required"
    }
    
    container('docker') {
        script {
            echo "Installing sonar-scanner..."
            sh '''
                # Install sonar-scanner if not already available
                if ! command -v sonar-scanner &> /dev/null; then
                    echo "Installing sonar-scanner..."
                    
                    # Download and install sonar-scanner
                    cd /tmp
                    wget -q https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-5.0.1.3006-linux.zip
                    unzip -q sonar-scanner-cli-5.0.1.3006-linux.zip
                    mv sonar-scanner-5.0.1.3006-linux /opt/sonar-scanner
                    ln -sf /opt/sonar-scanner/bin/sonar-scanner /usr/local/bin/sonar-scanner
                    
                    echo "sonar-scanner installed successfully"
                else
                    echo "sonar-scanner is already available"
                fi
            '''
            
            echo "Creating sonar-project.properties file..."
            writeFile file: 'sonar-project.properties', text: """
sonar.projectKey=${config.projectKey}
sonar.projectName=${config.projectKey}
sonar.projectVersion=1.0
sonar.sources=.
sonar.exclusions=**/node_modules/**,**/target/**,**/*.test.js,**/*.spec.js,**/test/**,**/tests/**,**/__tests__/**,**/coverage/**,**/build/**,**/dist/**
sonar.host.url=${config.sonarHostUrl}
"""
            
            echo "Running SonarQube analysis..."
            sh """
                export SONAR_TOKEN='${config.sonarToken}'
                sonar-scanner -Dsonar.login=\$SONAR_TOKEN
            """
            
            echo "SonarQube analysis completed successfully!"
        }
    }
}