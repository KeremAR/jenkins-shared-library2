def call(Map config) {
    echo "🚀 Deploying ALL services to production from tag: ${env.TAG_NAME}"
    
    // Validate that we have a tag
    if (!env.TAG_NAME) {
        error("❌ TAG_NAME is not set. Production deployment requires a git tag (e.g., v1.0.0)")
    }
    
    echo "📦 Tag: ${env.TAG_NAME}"
    
    // List of services to deploy
    def services = config.services ?: ['user-service', 'todo-service', 'frontend']
    
    // STEP 1: Get current staging image tag for each service
    echo "🔍 Step 1: Getting current staging image tags for all services..."
    def serviceImageTags = [:]
    
    for (serviceName in services) {
        def manifestFile = "argocd-manifests/environments/staging/staging-${serviceName}.yaml"
        def imageTag = sh(
            script: """
                git clone https://github.com/KeremAR/gitops-epam.git temp_staging_check_${serviceName}
                cd temp_staging_check_${serviceName}
                grep "name: image.tag" ${manifestFile} -A 1 | tail -1 | sed "s/.*value: '\\(.*\\)'/\\1/"
            """,
            returnStdout: true
        ).trim()
        
        serviceImageTags[serviceName] = imageTag
        echo "🏷️  ${serviceName}: ${imageTag}"
        sh "rm -rf temp_staging_check_${serviceName}"
    }
    
    // STEP 2: Update GitOps manifests for ALL services
    echo "📝 Step 2: Updating GitOps manifests for all services..."
    for (serviceName in services) {
        def imageTag = serviceImageTags[serviceName]
        echo "   Updating ${serviceName} with tag ${imageTag}..."
        
        updateGitOpsManifest([
            imageTag: imageTag,
            environment: 'production',
            serviceName: serviceName,
            targetRevision: env.TAG_NAME,
            gitOpsRepo: config.gitOpsRepo,
            gitPushCredentialId: config.gitPushCredentialId
        ])
    }
    
    // Wait a moment for GitHub to process the commits
    echo "⏳ Waiting for GitHub to process the commits..."
    sleep(time: 10, unit: 'SECONDS')
    
    // STEP 3: Sync ROOT-APP and wait for all service apps
    echo "🔄 Step 3: Syncing ROOT ArgoCD application (App-of-Apps)..."
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'
    def rootAppName = config.argoCdRootAppName ?: 'root-app'

    container('argo') {
        withCredentials([
            string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
            string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
        ]) {
            withEnv([
                "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
                "ROOT_APP_NAME=${rootAppName}"
            ]) {
                sh """
                    echo "🔐 Logging into ArgoCD..."
                    argocd login \$ARGOCD_SERVER --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                    
                    echo ""
                    echo "🔄 Step 3a: Syncing ROOT-APP (watches argocd-manifests/)..."
                    argocd app sync \$ROOT_APP_NAME
                    echo "✅ Root-app synced! It will now update all production service apps."
                    
                    echo ""
                    echo "⏳ Step 3b: Waiting for all PRODUCTION service apps to be healthy..."
                """
                
                // Wait for each service app
                for (serviceName in services) {
                    def serviceAppName = "production-${serviceName}"
                    sh """
                        echo "   Waiting for ${serviceAppName}..."
                        argocd app wait ${serviceAppName} --health --sync --timeout 600
                        echo "   ✅ ${serviceAppName} is healthy!"
                    """
                }
                
                sh """
                    echo ""
                    echo "✅ Production deployment complete for all services!"
                    echo ""
                    echo "📊 Root Application Status:"
                    argocd app get \$ROOT_APP_NAME
                """
                
                // Show status of all service apps
                for (serviceName in services) {
                    def serviceAppName = "production-${serviceName}"
                    sh """
                        echo ""
                        echo "📊 ${serviceAppName} Status:"
                        argocd app get ${serviceAppName}
                    """
                }
            }
        }
    }
}