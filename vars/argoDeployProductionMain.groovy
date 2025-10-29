def call(Map config) {
    echo "🚀 Deploying to production from tag: ${env.TAG_NAME}"
    
    // Validate that we have a tag
    if (!env.TAG_NAME) {
        error("❌ TAG_NAME is not set. Production deployment requires a git tag (e.g., v1.0.0)")
    }
    
    echo "📦 Tag: ${env.TAG_NAME}"
    echo "🏷️  Build: ${env.IMAGE_TAG}"
    
    // STEP 1: Update GitOps manifest with new image tags AND targetRevision
    echo "📝 Step 1: Updating GitOps manifest (gitops-epam)..."
    updateGitOpsManifest([
        imageTag: env.IMAGE_TAG,
        environment: 'production',
        targetRevision: env.TAG_NAME,  // ← Tag'i targetRevision olarak set et
        gitOpsRepo: config.gitOpsRepo,
        gitPushCredentialId: config.gitPushCredentialId
    ])
    
    // Wait a moment for GitHub to process the commit and ArgoCD to detect change
    echo "⏳ Waiting for ArgoCD to detect changes..."
    sleep(time: 10, unit: 'SECONDS')
    
    // STEP 2: Sync ArgoCD
    echo "🔄 Step 2: Syncing ArgoCD application..."
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'

    container('argo') {
        withCredentials([
            string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
            string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
        ]) {
            withEnv([
                "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
                "ARGO_APP_NAME=${config.argoCdProdAppName}"
            ]) {
                sh """
                    echo "Syncing ArgoCD application..."
                    argocd login \$ARGOCD_SERVER --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                    argocd app sync \$ARGO_APP_NAME
                    argocd app wait \$ARGO_APP_NAME --health --timeout 600
                    
                    echo "✅ Production deployment complete!"
                    echo "📊 Application status:"
                    argocd app get \$ARGO_APP_NAME
                """
            }
        }
    }
}