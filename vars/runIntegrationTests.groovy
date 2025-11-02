#!/usr/bin/env groovy

/**
 * Run Real E2E Integration Tests (Generic Library)
 * 
 * This is a GENERIC library that:
 * 1. Starts docker-compose stack with pre-built images
 * 2. Waits for services to be healthy
 * 3. Executes PROJECT-SPECIFIC test script
 * 4. Cleans up environment
 * 
 * @param config Map containing:
 *   - composeFile: docker-compose file to use (default: 'docker-compose.ci.yml')
 *   - userServiceUrl: URL for user service (default: 'http://localhost:8001')
 *   - todoServiceUrl: URL for todo service (default: 'http://localhost:8002')
 *   - healthCheckTimeout: Max seconds to wait for services (default: 120)
 *   - builtImages: Comma-separated string of pre-built images from Build stage (REQUIRED)
 *   - imageTag: Current build image tag (e.g., BUILD_NUMBER) to filter versioned images
 *   - testScriptPath: Path to project-specific test script (default: 'scripts/e2e-test.sh')
 */
def call(Map config = [:]) {
    def composeFile = config.composeFile ?: 'docker-compose.ci.yml'
    def userServiceUrl = config.userServiceUrl ?: 'http://localhost:8001'
    def todoServiceUrl = config.todoServiceUrl ?: 'http://localhost:8002'
    def healthCheckTimeout = config.healthCheckTimeout ?: 120
    def builtImages = config.builtImages
    def imageTag = config.imageTag
    def testScriptPath = config.testScriptPath ?: 'scripts/e2e-test.sh'
    
    // Parse versioned images from builtImages string (exclude :latest tags)
    def allImages = builtImages.split(',')
    def userServiceImage = allImages.find { it.contains('user-service') && it.contains(":${imageTag}") }
    def todoServiceImage = allImages.find { it.contains('todo-service') && it.contains(":${imageTag}") }
    def frontendImage = allImages.find { it.contains('frontend') && it.contains(":${imageTag}") }
    
    // Validate that all required images were found
    if (!userServiceImage || !todoServiceImage || !frontendImage) {
        error("❌ Failed to parse required images from builtImages. Found: user=${userServiceImage}, todo=${todoServiceImage}, frontend=${frontendImage}")
    }
    
    container('docker') {
        // Set environment variables for ALL sh blocks (including test script and finally block)
        withEnv([
            "USER_SERVICE_IMAGE=${userServiceImage}",
            "TODO_SERVICE_IMAGE=${todoServiceImage}",
            "FRONTEND_IMAGE=${frontendImage}",
            "USER_SERVICE_URL=${userServiceUrl}",
            "TODO_SERVICE_URL=${todoServiceUrl}"
        ]) {
            try {
                echo "Installing test dependencies..."
                sh "apk update && apk add bash curl"      
                
                      
                echo "🚀 Starting real E2E integration tests..."
                echo "   Compose file: ${composeFile}"
                echo "   User Service: ${userServiceUrl}"
                echo "   Todo Service: ${todoServiceUrl}"
                echo "   Using pre-built images:"
                echo "      User Service: ${userServiceImage}"
                echo "      Todo Service: ${todoServiceImage}"
                echo "      Frontend: ${frontendImage}"
                
                // Step 1: Clean up any existing containers and volumes
                echo "🧹 Cleaning up previous test environment..."
                sh """
                    docker compose -f ${composeFile} down -v 2>/dev/null || true
                """
                
                // Step 2: Start all services with pre-built images
                echo "🚀 Starting application stack with pre-built images..."
                sh """
                    # Environment variables are already set by withEnv block
                    # Start services (NO BUILD - using pre-built images)
                    docker compose -f ${composeFile} up -d
                """
            
            // Step 3: Wait for services to be healthy
            echo "⏳ Waiting for services to be ready (timeout: ${healthCheckTimeout}s)..."
            
            // Wait for user-service
            echo "   Checking user-service health..."
            sh """
                timeout ${healthCheckTimeout} sh -c '
                    until curl -sf ${userServiceUrl}/health > /dev/null 2>&1; do
                        echo "Waiting for user-service..."
                        sleep 2
                    done
                '
            """
            echo "   ✅ User-service is ready!"
            
            // Wait for todo-service
            echo "   Checking todo-service health..."
            sh """
                timeout ${healthCheckTimeout} sh -c '
                    until curl -sf ${todoServiceUrl}/health > /dev/null 2>&1; do
                        echo "Waiting for todo-service..."
                        sleep 2
                    done
                '
            """
            echo "   ✅ Todo-service is ready!"
            
            // Step 4: Run project-specific E2E test script
            echo "🧪 Running project-specific E2E integration tests..."
            echo "   Test script: ${testScriptPath}"
            echo "   Environment variables are available:"
            echo "      USER_SERVICE_URL=${userServiceUrl}"
            echo "      TODO_SERVICE_URL=${todoServiceUrl}"
            
            sh """
                # Make test script executable
                chmod +x ${testScriptPath}
                
                # Execute project-specific test script
                # Environment variables (USER_SERVICE_URL, TODO_SERVICE_URL) are available via withEnv
                ${testScriptPath}
            """
            
            echo "🎉 All E2E integration tests passed!"
            
        } catch (Exception e) {
            echo "❌ E2E integration tests failed!"
            echo "Error: ${e.message}"
            
            // Show container logs for debugging
            echo "📋 Service logs for debugging:"
            sh """
                echo "=== User Service Logs ==="
                docker compose -f ${composeFile} logs user-service --tail=50 || true
                echo ""
                echo "=== Todo Service Logs ==="
                docker compose -f ${composeFile} logs todo-service --tail=50 || true
                echo ""
                echo "=== Database Logs ==="
                docker compose -f ${composeFile} logs user-db --tail=30 || true
                docker compose -f ${composeFile} logs todo-db --tail=30 || true
            """
            
                throw e
                
            } finally {
                // Cleanup: Stop and remove containers/volumes
                // Environment variables from withEnv are still available here
                echo "🧹 Cleaning up test environment..."
                sh """
                    docker compose -f ${composeFile} down -v || true
                """
            }
        } // End withEnv
    } // End container
}
