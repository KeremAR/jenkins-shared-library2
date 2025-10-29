def call(Map config) {
    echo "🚀 Deploying to production from tag: ${env.TAG_NAME}"
    
    // Validate that we have a tag
    if (!env.TAG_NAME) {
        error("❌ TAG_NAME is not set. Production deployment requires a git tag (e.g., v1.0.0)")
    }
    
    echo "📦 Tag: ${env.TAG_NAME}"
    
    // STEP 1: Get current staging image tag (the images we want to promote to production)
    echo "🔍 Step 1: Getting current staging image tag..."
    def stagingImageTag = sh(
        script: '''
            git clone https://github.com/KeremAR/gitops-epam.git temp_staging_check
            cd temp_staging_check
            grep "name: frontend.image.tag" argocd-manifests/environments/staging.yaml -A 1 | tail -1 | sed "s/.*value: '\\(.*\\)'/\\1/"
        ''',
        returnStdout: true
    ).trim()
    
    echo "🏷️  Staging Image Tag: ${stagingImageTag}"
    echo "📌 This is the image tag that will be promoted to production"
    
    sh "rm -rf temp_staging_check"
    
    // STEP 2: Update GitOps manifest with staging image tags AND targetRevision
    echo "📝 Step 2: Updating GitOps manifest (gitops-epam)..."
    updateGitOpsManifest([
        imageTag: stagingImageTag,  // ← Staging'deki image tag'ini kullan
        environment: 'production',
        targetRevision: env.TAG_NAME,  // ← Tag'i targetRevision olarak set et
        gitOpsRepo: config.gitOpsRepo,
        gitPushCredentialId: config.gitPushCredentialId
    ])
    
    // Wait a moment for GitHub to process the commit and ArgoCD to detect change
    echo "⏳ Waiting for ArgoCD to detect changes..."
    sleep(time: 15, unit: 'SECONDS')
    
    // STEP 3: Sync ArgoCD
    echo "🔄 Step 3: Syncing ArgoCD application..."
    
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