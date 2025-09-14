#!/usr/bin/env groovy

def call(Map config) {
    def services = config.services

    container('docker') {
        echo "🧪 Running unit tests for backend services..."
        
        services.each { service ->
            def serviceName = service.name
            def dockerfilePath = service.dockerfile ?: "${serviceName}/Dockerfile"
            def contextPath = service.context ?: "."

            try {
                echo "Testing ${serviceName}..."
                // Build the 'test' stage from the Dockerfile and run it
                sh """
                    docker build \\
                        --target test \\
                        -t ${serviceName}-test-runner \\
                        -f ${dockerfilePath} \\
                        ${contextPath}
                """
                echo "✅ ${serviceName} unit tests passed!"
            } catch (e) {
                echo "❌ ${serviceName} unit tests failed!"
                throw e
            }
        }
        
        echo "🎉 All unit tests completed!"
    }
}
