#!/usr/bin/env groovy

def call(Map config) {
    def services = config.services

    container('docker') {
        echo "🧪 Running unit tests and generating coverage reports for backend services in parallel..."
        
        // Ensure the coverage reports directory exists and is clean
        sh "mkdir -p coverage-reports && rm -f coverage-reports/*.xml"

        def parallelTests = [:]
        
        services.each { service ->
            def serviceName = service.name
            def dockerfilePath = service.dockerfile ?: "${serviceName}/Dockerfile.test"
            def contextPath = service.context ?: "."

            parallelTests["Test ${serviceName}"] = {
                stage("Test ${serviceName}") {
                    try {
                        echo "Building test image for ${serviceName}..."
                        sh """
                            docker build \\
                                --target test \\
                                -t ${serviceName}-test-runner \\
                                -f ${dockerfilePath} \\
                                ${contextPath}
                        """

                        echo "Running tests for ${serviceName} and generating coverage report..."
                        sh """
                            docker run --rm \\
                                -v ${env.WORKSPACE}/coverage-reports:/app/coverage-reports \\
                                ${serviceName}-test-runner \\
                                pytest --cov=. --cov-report=xml:/app/coverage-reports/coverage-${serviceName}.xml
                        """

                        echo "Fixing coverage report paths for ${serviceName}..."
                        sh "sed -i 's|filename=\"|filename=\"${serviceName}/|g' ${env.WORKSPACE}/coverage-reports/coverage-${serviceName}.xml"

                        echo "✅ ${serviceName} unit tests passed!"
                    } catch (e) {
                        echo "❌ ${serviceName} unit tests failed!"
                        throw e
                    }
                }
            }
        }
        
        parallel parallelTests
        
        echo "🎉 All unit tests completed!"
    }
}
