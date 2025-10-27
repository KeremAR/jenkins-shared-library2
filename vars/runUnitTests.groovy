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
                        def containerName = "test-runner-${serviceName}-${env.BUILD_NUMBER}"
                        try {
                            sh """
                                docker run --name ${containerName} \\
                                    ${serviceName}-test-runner \\
                                    pytest -p no:cacheprovider --cov=. --cov-report=xml:coverage.xml
                            """
                        } finally {
                            echo "Copying coverage report from container..."
                            sh "docker cp ${containerName}:/app/coverage.xml ${env.WORKSPACE}/coverage-reports/coverage-${serviceName}.xml"
                            sh "docker rm ${containerName}"
                            
                            // Fix file permissions (docker cp creates files as root)
                            sh "chmod 666 ${env.WORKSPACE}/coverage-reports/coverage-${serviceName}.xml"
                        }

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
        
        // Fix directory permissions after all docker cp operations
        // (docker cp sets directory ownership to root, causing deleteDir to fail)
        echo "Fixing coverage-reports directory permissions..."
        sh "chmod -R 777 coverage-reports"
        
        echo "🎉 All unit tests completed!"
    }
}
