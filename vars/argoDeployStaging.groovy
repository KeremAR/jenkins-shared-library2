/**
 * Deploys a specific service to staging environment using ArgoCD (Service-Based App-of-Apps Pattern).
 *
 * IMPORTANT: This script uses Service-Based App-of-Apps pattern:
 * 1. Updates GitOps manifest for SPECIFIC SERVICE (e.g., staging-user-service.yaml)
 * 2. Syncs ROOT-APP (which watches argocd-manifests/)
 * 3. Root app updates child app for the specific service
 * 4. Waits for that specific service's app to be healthy
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - serviceName: The name of the service to deploy (e.g., 'user-service', 'todo-service', 'frontend')
 *   - argoCdUserCredentialId: The ID of the Jenkins secret text credential for the ArgoCD username.
 *   - argoCdPassCredentialId: The ID of the Jenkins secret text credential for the ArgoCD password.
 *   - argoCdRootAppName: The name of the ROOT ArgoCD application (e.g., 'root-app')
 *   - gitOpsRepo: The GitOps repository URL
 *   - gitPushCredentialId: Git credentials for pushing manifest updates
 */
def call(Map config) {
    def serviceName = config.serviceName
    
    if (!serviceName) {
        error "❌ serviceName is required for service-based deployment"
    }
    
    echo "🚀 Deploying ${serviceName} to staging from main branch..."
    
    // STEP 1: Update GitOps manifest for this specific service
    echo "📝 Step 1: Updating GitOps manifest for ${serviceName}..."
    updateGitOpsManifest([
        imageTag: env.IMAGE_TAG,
        environment: 'staging',
        serviceName: serviceName,  // NEW: Pass service name
        gitOpsRepo: config.gitOpsRepo,
        gitPushCredentialId: config.gitPushCredentialId
    ])
    
    // Wait a moment for GitHub to process the commit
    echo "⏳ Waiting for GitHub to process the commit..."
    sleep(time: 5, unit: 'SECONDS')
    
    // STEP 2: Sync ROOT-APP and wait for service app
    echo "🔄 Step 2: Syncing ROOT ArgoCD application (App-of-Apps)..."
    echo "   ⚠️  IMPORTANT: We sync ROOT-APP, not individual service apps!"
    echo "   Reason: GitOps manifest changes are in argocd-manifests/"
    echo "           which is watched by root-app"
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'
    def rootAppName = config.argoCdRootAppName ?: 'root-app'
    
    // Construct service app name: staging-{serviceName}
    def serviceAppName = "staging-${serviceName}"

    container('argo') {
        withCredentials([
            string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
            string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
        ]) {
            withEnv([
                "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
                "ROOT_APP_NAME=${rootAppName}",
                "SERVICE_APP_NAME=${serviceAppName}",
                "SERVICE_NAME=${serviceName}"
            ]) {
                sh """
                    echo "🔐 Logging into ArgoCD..."
                    argocd login \$ARGOCD_SERVER --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                    
                    echo ""
                    echo "🔄 Step 2a: Syncing ROOT-APP (watches argocd-manifests/)..."
                    argocd app sync \$ROOT_APP_NAME
                    echo "✅ Root-app synced! It will now update \$SERVICE_APP_NAME."
                    
                    echo ""
                    echo "⏳ Step 2b: Waiting for SERVICE-APP (\$SERVICE_APP_NAME) to be healthy..."
                    echo "   This ensures new image tag (:${env.IMAGE_TAG}) for \$SERVICE_NAME is deployed."
                    argocd app wait \$SERVICE_APP_NAME --health --sync --timeout 600
                    
                    echo ""
                    echo "✅ Staging deployment complete for \$SERVICE_NAME!"
                    echo ""
                    echo "📊 Root Application Status:"
                    argocd app get \$ROOT_APP_NAME
                    echo ""
                    echo "📊 Service Application Status (\$SERVICE_APP_NAME):"
                    argocd app get \$SERVICE_APP_NAME
                """
            }
        }
    }
}
