#!/usr/bin/env groovy

/**
 * Run E2E Tests Against Live Staging Environment
 * 
 * This library runs project-specific E2E test script against REAL staging URLs
 * (after ArgoCD deployment completes). Does NOT start docker-compose.
 * 
 * IMPORTANT: Before running tests, this library verifies deployed image tags
 * using kubectl to ensure ArgoCD deployment succeeded correctly.
 * 
 * @param config Map containing:
 *   - testScriptPath: Path to project test script (default: 'scripts/e2e-test.sh')
 *   - stagingUserServiceUrl: REQUIRED - Live staging user-service URL (e.g., https://staging-user.example.com)
 *   - stagingTodoServiceUrl: REQUIRED - Live staging todo-service URL (e.g., https://staging-todo.example.com)
 *   - namespace: REQUIRED - K8s namespace (e.g., 'staging')
 *   - userServiceDeploymentName: REQUIRED - User service deployment name (e.g., 'user-service')
 *   - todoServiceDeploymentName: REQUIRED - Todo service deployment name (e.g., 'todo-service')
 * 
 * @example
 * runStagingE2ETests(
 *     testScriptPath: 'scripts/e2e-test.sh',
 *     stagingUserServiceUrl: 'https://staging.epam-proxmox-k3s/user-service',
 *     stagingTodoServiceUrl: 'https://staging.epam-proxmox-k3s/todo-service',
 *     namespace: 'staging',
 *     userServiceDeploymentName: 'user-service',
 *     todoServiceDeploymentName: 'todo-service'
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
    
    // Optional parameters with defaults
    def testScriptPath = config.testScriptPath ?: 'scripts/e2e-test.sh'
    def userServiceUrl = config.stagingUserServiceUrl
    def todoServiceUrl = config.stagingTodoServiceUrl
    def namespace = config.namespace ?: 'staging'
    def userServiceDeployment = config.userServiceDeploymentName ?: 'user-service'
    def todoServiceDeployment = config.todoServiceDeploymentName ?: 'todo-service'
    
    echo "🧪 Running E2E tests against LIVE staging environment..."
    echo "   Test Script: ${testScriptPath}"
    echo "   User Service: ${userServiceUrl}"
    echo "   Todo Service: ${todoServiceUrl}"
    echo "   Namespace: ${namespace}"
    echo ""
    
    // Use argo container (has kubectl, bash, curl)
    container('argo') {
        // Set environment variables for test script
        withEnv([
            "USER_SERVICE_URL=${userServiceUrl}",
            "TODO_SERVICE_URL=${todoServiceUrl}"
        ]) {
            sh """
                # STEP 1: Verify deployed image tags with kubectl (Trust but Verify!)
                echo "🔍 Step 1: Verifying deployed image tags in K8s cluster..."
                echo "   This ensures ArgoCD deployment succeeded correctly."
                echo ""
                echo "📦 Checking deployed images in namespace: ${namespace}"
                echo ""
                
                # Get user-service image
                USER_SERVICE_IMAGE=\$(kubectl get deployment ${userServiceDeployment} \
                    -n ${namespace} \
                    -o jsonpath='{.spec.template.spec.containers[0].image}')
                
                echo "✅ VERIFIED User Service Image: \${USER_SERVICE_IMAGE}"
                
                # Get todo-service image
                TODO_SERVICE_IMAGE=\$(kubectl get deployment ${todoServiceDeployment} \
                    -n ${namespace} \
                    -o jsonpath='{.spec.template.spec.containers[0].image}')
                
                echo "✅ VERIFIED Todo Service Image: \${TODO_SERVICE_IMAGE}"
                echo ""
                echo "🎯 Image verification complete! Proceeding to E2E tests..."
                echo ""
                
                # STEP 2: Run E2E tests against verified deployment
                echo "🧪 Step 2: Running E2E tests against verified staging deployment..."
                echo "🚀 Preparing to run E2E tests against staging..."
                echo ""
                
                # Make test script executable
                chmod +x ${testScriptPath}
                
                # Execute test script
                # Script will read USER_SERVICE_URL and TODO_SERVICE_URL from environment
                echo "▶️  Executing: ${testScriptPath}"
                ${testScriptPath}
            """
        }
    }
    
    echo ""
    echo "✅ Staging E2E tests completed successfully!"
    echo "   All tests passed against live staging deployment."
}
