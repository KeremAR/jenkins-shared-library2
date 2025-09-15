#!/usr/bin/env groovy

def call(Map config) {
    def services = config.services

    container('docker') {
        echo "🧪 Running unit tests with coverage for backend services in parallel..."
        
        def parallelTests = [:]
        
        services.each { service ->
            def serviceName = service.name
            def dockerfilePath = service.dockerfile ?: "${serviceName}/Dockerfile.test"
            def contextPath = service.context ?: "."
            def reportPath = "reports/${serviceName}"

            parallelTests["Test ${serviceName}"] = {
                try {
                    // Ensure the report directory exists on the agent
                    sh "mkdir -p ${reportPath}"
                    
                    echo "Testing ${serviceName} and generating coverage report..."
                    
                    // Build the test stage
                    sh """
                        docker build \\
                            --target test \\
                            -t ${serviceName}-test-runner \\
                            -f ${dockerfilePath} \\
                            ${contextPath}
                    """

                    // Create a temporary container from the test image to run tests and extract the report
                    sh """
                        docker create --name ${serviceName}-test-container ${serviceName}-test-runner
                        docker cp ${serviceName}-test-container:/app/coverage.xml ./${reportPath}/coverage.xml
                        docker rm -f ${serviceName}-test-container
                    """

                    echo "✅ ${serviceName} unit tests and coverage report generation passed!"
                } catch (e) {
                    echo "❌ ${serviceName} unit tests or coverage generation failed!"
                    throw e
                }
            }
        }
        
        parallel parallelTests
        
        echo "🎉 All unit tests and coverage reports completed!"
    }
}
