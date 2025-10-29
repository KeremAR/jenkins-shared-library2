/**
 * Deploys the staging environment using ArgoCD.
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - argoCdUserCredentialId: The ID of the Jenkins secret text credential for the ArgoCD username.
 *   - argoCdPassCredentialId: The ID of the Jenkins secret text credential for the ArgoCD password.
 *   - argoCdStagingAppName: The name of the ArgoCD application for staging.
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
    
    // Wait a moment for GitHub to process the commit and ArgoCD to detect change
    echo "⏳ Waiting for ArgoCD to detect changes..."
    sleep(time: 10, unit: 'SECONDS')
    
    // STEP 2: Sync ArgoCD
    echo "🔄 Step 2: Syncing ArgoCD application for staging..."
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'

    container('argo') {
        withCredentials([
            string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
            string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
        ]) {
            withEnv([
                "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
                "ARGO_APP_NAME=${config.argoCdStagingAppName}"
            ]) {
                sh """
                    echo "Syncing ArgoCD application for staging..."
                    argocd login \$ARGOCD_SERVER --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                    argocd app sync \$ARGO_APP_NAME
                    argocd app wait \$ARGO_APP_NAME --health --timeout 600
                    
                    echo "✅ Staging deployment complete!"
                    echo "📊 Application status:"
                    argocd app get \$ARGO_APP_NAME
                """
            }
        }
    }
}
