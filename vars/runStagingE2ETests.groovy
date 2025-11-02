#!/usr/bin/env groovy

/**
 * Run E2E Tests Against Live Staging Environment
 * 
 * This library runs project-specific E2E test script against REAL staging URLs
 * (after ArgoCD deployment completes). Does NOT start docker-compose.
 * 
 * @param config Map containing:
 *   - testScriptPath: Path to project test script (default: 'scripts/e2e-test.sh')
 *   - stagingUserServiceUrl: REQUIRED - Live staging user-service URL (e.g., https://staging-user.example.com)
 *   - stagingTodoServiceUrl: REQUIRED - Live staging todo-service URL (e.g., https://staging-todo.example.com)
 * 
 * @example
 * runStagingE2ETests(
 *     testScriptPath: 'scripts/e2e-test.sh',
 *     stagingUserServiceUrl: 'https://staging.epam-proxmox-k3s/user-service',
 *     stagingTodoServiceUrl: 'https://staging.epam-proxmox-k3s/todo-service'
 * )
 */
def call(Map config = [:]) {
    // Validate required parameters
    if (!config.stagingUserServiceUrl) {
        error("❌ stagingUserServiceUrl is required! Please provide the live staging user-service URL.")
    }
    if (!config.stagingTodoServiceUrl) {
        error("❌ stagingTodoServiceUrl is required! Please provide the live staging todo-service URL.")
    }
    
    def testScriptPath = config.testScriptPath ?: 'scripts/e2e-test.sh'
    def userServiceUrl = config.stagingUserServiceUrl
    def todoServiceUrl = config.stagingTodoServiceUrl
    
    echo "🧪 Running E2E tests against LIVE staging environment..."
    echo "   Test Script: ${testScriptPath}"
    echo "   User Service: ${userServiceUrl}"
    echo "   Todo Service: ${todoServiceUrl}"
    echo ""
    
    // Use jnlp container (has bash, curl, and common utilities)
    container('jnlp') {
        // Set environment variables for test script
        withEnv([
            "USER_SERVICE_URL=${userServiceUrl}",
            "TODO_SERVICE_URL=${todoServiceUrl}"
        ]) {
            sh """
                echo "🚀 Preparing to run E2E tests against staging..."
                
                # Make test script executable
                chmod +x ${testScriptPath}
                
                # Execute test script
                # Script will read USER_SERVICE_URL and TODO_SERVICE_URL from environment
                echo "▶️  Executing: ${testScriptPath}"
                ${testScriptPath}
            """
        }
    }
    
    echo "✅ Staging E2E tests completed successfully!"
    echo "   All tests passed against live staging deployment."
}
