def call(Map config) {
    echo "📝 Updating GitOps manifest with new image tags..."
    
    def imageTag = config.imageTag ?: env.BUILD_NUMBER
    def environment = config.environment ?: 'production'
    def serviceName = config.serviceName  // NEW: Service name (e.g., 'user-service', 'todo-service', 'frontend')
    def gitOpsRepo = config.gitOpsRepo ?: 'github.com/KeremAR/gitops-epam'
    def gitPushCredentialId = config.gitPushCredentialId ?: 'github-webhook'
    def targetRevision = config.targetRevision  // Optional: only for production tags
    
    // Validate serviceName parameter
    if (!serviceName) {
        error "❌ serviceName parameter is required for service-based deployments"
    }
    
    // Construct manifest file path for specific service
    // Example: argocd-manifests/environments/staging/staging-user-service.yaml
    def manifestFile = "argocd-manifests/environments/${environment}/${environment}-${serviceName}.yaml"
    
    echo "📌 Target manifest file: ${manifestFile}"
    echo "📌 Service: ${serviceName}"
    echo "📌 Image tag: ${imageTag}"
    
    // Build sed commands for targetRevision update
    def targetRevisionCommands = ""
    if (targetRevision) {
        echo "📌 Will update targetRevision to: ${targetRevision}"
        targetRevisionCommands = """
            echo "Updating targetRevision to: ${targetRevision}"
            sed -i "s|targetRevision: '.*'|targetRevision: '${targetRevision}'|" ${manifestFile}
            sed -i "s|targetRevision: .*|targetRevision: '${targetRevision}'|" ${manifestFile}
        """
    } else {
        echo "📌 targetRevision will not be updated (using existing value)"
    }
    
    withCredentials([
        usernamePassword(credentialsId: gitPushCredentialId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')
    ]) {
        sh """
            echo "Cloning GitOps repository..."
            rm -rf temp_gitops_repo
            git clone "https://${GIT_USERNAME}:${GIT_PASSWORD}@${gitOpsRepo}.git" temp_gitops_repo
            cd temp_gitops_repo
            
            echo "Updating image tag for ${serviceName} in ${manifestFile}..."
            
            # Update image.tag parameter for this specific service
            sed -i '/name: image.tag/!b;n;c\\          value: '"'"'${imageTag}'"'"'' ${manifestFile}
            
            ${targetRevisionCommands}
            
            echo "Changes made to ${manifestFile}:"
            git diff ${manifestFile}
            
            # Commit and push
            git config --global user.email "jenkins@ci.local"
            git config --global user.name "Jenkins CI"
            git add ${manifestFile}
            
            if ! git diff-index --quiet HEAD; then
                echo "Committing GitOps manifest updates..."
                git commit -m "ci: Update ${environment} ${serviceName} to build ${imageTag}"
                
                echo "Pushing to GitOps repository..."
                git push origin main
                
                echo "✅ GitOps manifest updated successfully for ${serviceName}"
            else
                echo "No changes to commit"
            fi
            
            cd ..
            rm -rf temp_gitops_repo
        """
    }
}

