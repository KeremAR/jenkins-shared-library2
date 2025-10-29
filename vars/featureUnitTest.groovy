#!/usr/bin/env groovy

/**
 * Feature Branch Unit Test Runner
 * 
 * This function runs unit tests ONLY for services that have changed
 * compared to the main branch. This speeds up feedback for developers.
 * 
 * Features:
 * - Detects changed services via git diff against main branch
 * - Runs only tests for changed services
 * - No coverage reports (for speed)
 * - Fast feedback loop
 * 
 * Usage:
 *   featureUnitTest(services: config.unitTestServices)
 */
def call(Map config) {
    def services = config.services
    
    echo "🧪 Feature branch detected - running tests only for changed services..."
    
    // Use shared utility to detect changed services
    def changedServices = getChangedServices(services: services)
    
    if (changedServices.isEmpty()) {
        echo "⚠️ Skipping unit tests - no service changes detected."
        return
    }
    
    echo "📋 Running tests for ${changedServices.size()} changed service(s)"
    
    // Now run tests in docker container
    container('docker') {
        // Run tests for changed services in parallel
        def parallelTests = [:]
        
        changedServices.each { service ->
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
                        
                        echo "Running tests for ${serviceName} (no coverage for speed)..."
                        def containerName = "test-runner-${serviceName}-${env.BUILD_NUMBER}"
                        try {
                            sh """
                                docker run --name ${containerName} \\
                                    ${serviceName}-test-runner \\
                                    pytest -p no:cacheprovider
                            """
                        } finally {
                            sh "docker rm ${containerName} || true"
                        }
                        
                        echo "✅ ${serviceName} unit tests passed!"
                    } catch (e) {
                        echo "❌ ${serviceName} unit tests failed!"
                        throw e
                    }
                }
            }
        }
        
        parallel parallelTests
        
        echo "🎉 All changed services passed their unit tests!"
    }
}

