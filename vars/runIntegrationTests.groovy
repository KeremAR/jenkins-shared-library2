#!/usr/bin/env groovy

/**
 * Run Real E2E Integration Tests
 * 
 * Starts full application stack with docker-compose and runs E2E tests
 * that validate real service-to-service communication, JWT authentication,
 * and database interactions.
 * 
 * @param config Map containing:
 *   - composeFile: docker-compose file to use (default: 'docker-compose.ci.yml')
 *   - userServiceUrl: URL for user service (default: 'http://localhost:8001')
 *   - todoServiceUrl: URL for todo service (default: 'http://localhost:8002')
 *   - healthCheckTimeout: Max seconds to wait for services (default: 120)
 *   - userServiceImage: Pre-built user-service image (REQUIRED for CI)
 *   - todoServiceImage: Pre-built todo-service image (REQUIRED for CI)
 *   - frontendImage: Pre-built frontend image (REQUIRED for CI)
 */
def call(Map config = [:]) {
    def composeFile = config.composeFile ?: 'docker-compose.ci.yml'
    def userServiceUrl = config.userServiceUrl ?: 'http://localhost:8001'
    def todoServiceUrl = config.todoServiceUrl ?: 'http://localhost:8002'
    def healthCheckTimeout = config.healthCheckTimeout ?: 120
    def userServiceImage = config.userServiceImage
    def todoServiceImage = config.todoServiceImage
    def frontendImage = config.frontendImage
    
    container('docker') {
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
                docker system prune -f --volumes 2>/dev/null || true
            """
            
            // Step 2: Export image environment variables and start all services
            echo "� Starting application stack with pre-built images..."
            sh """
                # Export image variables for docker-compose.ci.yml
                export USER_SERVICE_IMAGE="${userServiceImage}"
                export TODO_SERVICE_IMAGE="${todoServiceImage}"
                export FRONTEND_IMAGE="${frontendImage}"
                
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
            
            // Step 4: Run E2E tests
            echo "🧪 Running E2E integration tests..."
            
            // Generate unique test user for this run
            def testTimestamp = System.currentTimeMillis()
            def testUser = "e2e-jenkins-${env.BUILD_NUMBER}-${testTimestamp}"
            
            sh """
                # Test 1: Health checks
                echo "Test 1: Health checks"
                curl -sf ${userServiceUrl}/health || exit 1
                curl -sf ${todoServiceUrl}/health || exit 1
                echo "✅ Health checks passed"
                
                # Test 2: User registration
                echo "Test 2: User registration"
                REGISTER_RESPONSE=\$(curl -sf -X POST ${userServiceUrl}/register \\
                    -H "Content-Type: application/json" \\
                    -d '{"username":"${testUser}","email":"${testUser}@test.com","password":"testpass123"}')
                echo "✅ User registration passed"
                
                # Test 3: User login and get JWT token
                echo "Test 3: User login and JWT token"
                TOKEN=\$(curl -sf -X POST ${userServiceUrl}/login \\
                    -H "Content-Type: application/json" \\
                    -d '{"username":"${testUser}","password":"testpass123"}' \\
                    | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
                
                if [ -z "\$TOKEN" ]; then
                    echo "❌ Failed to get JWT token"
                    exit 1
                fi
                echo "✅ JWT token received"
                
                # Test 4: Verify JWT token
                echo "Test 4: JWT token verification"
                curl -sf ${userServiceUrl}/verify \\
                    -H "Authorization: Bearer \$TOKEN" || exit 1
                echo "✅ JWT token verification passed"
                
                # Test 5: Create todo (User-service ↔ Todo-service integration)
                echo "Test 5: Create todo (service integration)"
                TODO_RESPONSE=\$(curl -sf -X POST ${todoServiceUrl}/todos \\
                    -H "Authorization: Bearer \$TOKEN" \\
                    -H "Content-Type: application/json" \\
                    -d '{"title":"Integration Test Todo","description":"E2E test from Jenkins"}')
                
                TODO_ID=\$(echo "\$TODO_RESPONSE" | grep -o '"id":[0-9]*' | cut -d':' -f2)
                if [ -z "\$TODO_ID" ]; then
                    echo "❌ Failed to create todo"
                    exit 1
                fi
                echo "✅ Todo created (ID: \$TODO_ID)"
                
                # Test 6: Get todos list
                echo "Test 6: Get todos list"
                curl -sf ${todoServiceUrl}/todos \\
                    -H "Authorization: Bearer \$TOKEN" || exit 1
                echo "✅ Todos list retrieved"
                
                # Test 7: Get specific todo
                echo "Test 7: Get specific todo"
                curl -sf ${todoServiceUrl}/todos/\$TODO_ID \\
                    -H "Authorization: Bearer \$TOKEN" || exit 1
                echo "✅ Specific todo retrieved"
                
                # Test 8: Update todo
                echo "Test 8: Update todo"
                curl -sf -X PUT ${todoServiceUrl}/todos/\$TODO_ID \\
                    -H "Authorization: Bearer \$TOKEN" \\
                    -H "Content-Type: application/json" \\
                    -d '{"completed":true}' || exit 1
                echo "✅ Todo updated"
                
                # Test 9: Delete todo
                echo "Test 9: Delete todo"
                curl -sf -X DELETE ${todoServiceUrl}/todos/\$TODO_ID \\
                    -H "Authorization: Bearer \$TOKEN" || exit 1
                echo "✅ Todo deleted"
                
                # Test 10: Verify unauthorized access is blocked
                echo "Test 10: Unauthorized access test"
                if curl -sf ${todoServiceUrl}/todos 2>/dev/null; then
                    echo "❌ Unauthorized access should be blocked!"
                    exit 1
                fi
                echo "✅ Unauthorized access properly blocked"
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
            echo "🧹 Cleaning up test environment..."
            sh """
                docker compose -f ${composeFile} down -v || true
            """
        }
    }
}
