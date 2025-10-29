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
    
    echo "🔍 Feature branch detected - running tests only for changed services..."
    
    // Fetch main branch to compare against (outside container, git is available here)
    echo "Fetching main branch for comparison..."
    sh """
        git fetch origin main || echo "Already fetched"
    """
    
    // Get list of changed files (outside container, git is available here)
    echo "Detecting changed files..."
    def changedFiles = sh(
        script: """
            # Try different methods to get changed files
            if git diff --name-only origin/main HEAD 2>/dev/null; then
                exit 0
            elif git diff --name-only FETCH_HEAD HEAD 2>/dev/null; then
                exit 0
            else
                # Fallback: compare with previous commit
                git diff --name-only HEAD~1 2>/dev/null || echo ""
            fi
        """,
        returnStdout: true
    ).trim()
    
    echo "Changed files:\n${changedFiles}"
    
    // Determine which services have changed
    def changedServices = []
    services.each { service ->
        def serviceName = service.name
        // Check if any file in the service directory was changed
        if (changedFiles.contains("${serviceName}/")) {
            changedServices.add(service)
            echo "✓ Service '${serviceName}' has changes"
        }
    }
    
    if (changedServices.isEmpty()) {
        echo "⚠️ No service changes detected. Skipping unit tests."
        echo "Changed files were:\n${changedFiles}"
        echo "This might be a documentation-only change or infrastructure change."
        return
    }
    
    echo "📋 Running tests for ${changedServices.size()} changed service(s): ${changedServices.collect { it.name }.join(', ')}"
    
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

