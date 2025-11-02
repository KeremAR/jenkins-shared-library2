/**
 * Deploys the staging environment using ArgoCD (App-of-Apps Pattern).
 *
 * IMPORTANT: This script uses App-of-Apps pattern:
 * 1. Updates GitOps manifest (staging.yaml in argocd-manifests/environments/)
 * 2. Syncs ROOT-APP (which watches argocd-manifests/)
 * 3. Root app updates child app (staging-todo-app) with new image tags
 * 4. Waits for child app (staging-todo-app) to be healthy
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - argoCdUserCredentialId: The ID of the Jenkins secret text credential for the ArgoCD username.
 *   - argoCdPassCredentialId: The ID of the Jenkins secret text credential for the ArgoCD password.
 *   - argoCdRootAppName: The name of the ROOT ArgoCD application (e.g., 'root-app')
 *   - argoCdStagingAppName: The name of the CHILD ArgoCD application for staging (e.g., 'staging-todo-app')
 *   - gitOpsRepo: The GitOps repository URL
 *   - gitPushCredentialId: Git credentials for pushing manifest updates
 */
def call(Map config) {
    echo "🚀 Deploying to staging from main branch..."
    
    // STEP 1: Update GitOps manifest with new image tags
    echo "📝 Step 1: Updating GitOps manifest (gitops-epam) for staging..."
    updateGitOpsManifest([
        imageTag: env.IMAGE_TAG,
        environment: 'staging',
        gitOpsRepo: config.gitOpsRepo,
        gitPushCredentialId: config.gitPushCredentialId
    ])
    
    // Wait a moment for GitHub to process the commit
    echo "⏳ Waiting for GitHub to process the commit..."
    sleep(time: 5, unit: 'SECONDS')
    
    // STEP 2: Sync ROOT-APP (App-of-Apps Pattern)
    echo "🔄 Step 2: Syncing ROOT ArgoCD application (App-of-Apps)..."
    echo "   ⚠️  IMPORTANT: We sync ROOT-APP, not child app!"
    echo "   Reason: GitOps manifest changes are in argocd-manifests/"
    echo "           which is watched by root-app, NOT staging-todo-app"
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'
    def rootAppName = config.argoCdRootAppName ?: 'root-app'
    def stagingAppName = config.argoCdStagingAppName

    container('argo') {
        withCredentials([
            string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
            string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
        ]) {
            withEnv([
                "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
                "ROOT_APP_NAME=${rootAppName}",
                "STAGING_APP_NAME=${stagingAppName}"
            ]) {
                sh """
                    echo "🔐 Logging into ArgoCD..."
                    argocd login \$ARGOCD_SERVER --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                    
                    echo ""
                    echo "🔄 Step 2a: Syncing ROOT-APP (watches argocd-manifests/)..."
                    argocd app sync \$ROOT_APP_NAME
                    echo "✅ Root-app synced! It will now update staging-todo-app parameters."
                    
                    echo ""
                    echo "⏳ Step 2b: Waiting for STAGING-APP to be healthy..."
                    echo "   This ensures new image tags (:${env.IMAGE_TAG}) are deployed."
                    argocd app wait \$STAGING_APP_NAME --health --sync --timeout 600
                    
                    echo ""
                    echo "✅ Staging deployment complete!"
                    echo ""
                    echo "📊 Root Application Status:"
                    argocd app get \$ROOT_APP_NAME
                    echo ""
                    echo "📊 Staging Application Status:"
                    argocd app get \$STAGING_APP_NAME
                """
            }
        }
    }
}
