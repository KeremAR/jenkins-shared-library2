#!/usr/bin/env groovy

def call(Map config = [:]) {
    def services = config.services
    def composeFile = config.composeFile ?: 'docker-compose.test.yml'
    
    container('docker') {
        echo "🧪 Running backend tests..."
        
        services.each { service ->
            echo "Testing ${service}..."
            sh "docker compose -f ${composeFile} run --rm -T ${service}-test"
            echo "✅ ${service} tests passed!"
        }
        
        echo "🎉 All tests completed successfully!"
    }
}