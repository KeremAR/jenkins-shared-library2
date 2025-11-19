/**
 * Deploys service(s) to staging environment using ArgoCD (Service-Based App-of-Apps Pattern).
 *
 * Supports both single-service and batch deployment:
 * - Single: Pass serviceName parameter
 * - Batch: Pass services array parameter
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - serviceName: Single service name (e.g., 'user-service') OR
 *   - services: Array of service names (e.g., ['user-service', 'todo-service', 'frontend'])
 *   - argoCdUserCredentialId: ArgoCD username credential ID
 *   - argoCdPassCredentialId: ArgoCD password credential ID
 *   - argoCdRootAppName: ROOT ArgoCD application name (e.g., 'root-app')
 *   - gitOpsRepo: GitOps repository URL
 *   - gitPushCredentialId: Git credentials for pushing manifest updates
 */
def call(Map config) {
    // Determine if batch or single service deployment
    def isBatch = config.services != null
    def servicesInput = isBatch ? config.services : [config.serviceName]
    
    // Normalize input: If services are Maps (from config.services), extract names. If Strings, keep as is.
    def servicesToDeploy = servicesInput.collect { service ->
        service instanceof Map ? service.name : service
    }
    
    if (!servicesToDeploy || servicesToDeploy.isEmpty()) {
        error "❌ Either serviceName or services array is required"
    }
    
    echo "🚀 Deploying ${servicesToDeploy.size()} service(s) to staging..."
    echo "📋 Services: ${servicesToDeploy.join(', ')}"
    
    // STEP 1: Update GitOps manifests (each service = separate commit)
    echo "📝 Step 1: Updating GitOps manifests with separate commits..."
    for (serviceName in servicesToDeploy) {
        echo "   📝 Updating ${serviceName}..."
        updateGitOpsManifest([
            imageTag: env.IMAGE_TAG,
            environment: 'staging',
            serviceName: serviceName,
            gitOpsRepo: config.gitOpsRepo,
            gitPushCredentialId: config.gitPushCredentialId
        ])
    }
    
    // Wait for GitHub to process the last commit
    echo "⏳ Waiting for GitHub to process..."
    sleep(time: 5, unit: 'SECONDS')
    
    // STEP 2: ArgoCD login ONCE + root-app sync ONCE
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'
    def rootAppName = config.argoCdRootAppName ?: 'root-app'
    
    echo "🔄 Step 2: Syncing ArgoCD..."
    container('argo') {
        withCredentials([
            string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
            string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
        ]) {
            sh """
                echo "🔐 Logging into ArgoCD..."
                argocd login ${env.ARGOCD_SERVER} --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                
                echo "🔄 Syncing ROOT-APP..."
                argocd app sync ${rootAppName}
                echo "✅ Root-app synced!"
            """
            
            // STEP 3: Wait for each service app (in loop)
            echo "⏳ Step 3: Waiting for each service app..."
            for (serviceName in servicesToDeploy) {
                def serviceAppName = "staging-${serviceName}"
                echo "   ⏳ Waiting for ${serviceAppName}..."
                sh """
                    argocd app wait ${serviceAppName} --health --sync --timeout 600
                    echo "   ✅ ${serviceAppName} is healthy!"
                """
            }
        }
    }
    
    echo "✅ All ${servicesToDeploy.size()} service(s) deployed to staging successfully!"
}
