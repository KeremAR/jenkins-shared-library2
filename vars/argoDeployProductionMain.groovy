def call(Map config) {
    echo "🚀 Deploying to production from main branch..."
    
    // STEP 1: Update Helm values with new image tags
    echo "📝 Step 1: Updating Helm image tags in values-prod.yaml..."
    updateHelmImageTags([
        imageTag: env.IMAGE_TAG,
        helmValuesFile: config.helmValuesProdFile ?: 'helm-charts/todo-app/values-prod.yaml',
        services: config.services,
        gitPushCredentialId: config.gitPushCredentialId
    ])
    
    // Wait a moment for GitHub to process the commit
    sleep(time: 5, unit: 'SECONDS')
    
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