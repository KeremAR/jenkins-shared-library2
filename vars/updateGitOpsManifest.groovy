def call(Map config) {
    echo "📝 Updating GitOps manifest with new image tags..."
    
    def imageTag = config.imageTag ?: env.BUILD_NUMBER
    def environment = config.environment ?: 'production'
    def manifestFile = config.manifestFile ?: "argocd-manifests/environments/${environment}.yaml"
    def gitOpsRepo = config.gitOpsRepo ?: 'github.com/KeremAR/gitops-epam'
    def gitPushCredentialId = config.gitPushCredentialId ?: 'github-webhook'
    
    withCredentials([
        usernamePassword(credentialsId: gitPushCredentialId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')
    ]) {
        sh """
            echo "Cloning GitOps repository..."
            rm -rf temp_gitops_repo
            git clone "https://${GIT_USERNAME}:${GIT_PASSWORD}@${gitOpsRepo}.git" temp_gitops_repo
            cd temp_gitops_repo
            
            echo "Updating image tags in ${manifestFile}..."
            
            # Update frontend image tag
            sed -i '/name: frontend.image.tag/!b;n;c\\          value: '"'"'${imageTag}'"'"'' ${manifestFile}
            
            # Update userService image tag
            sed -i '/name: userService.image.tag/!b;n;c\\          value: '"'"'${imageTag}'"'"'' ${manifestFile}
            
            # Update todoService image tag
            sed -i '/name: todoService.image.tag/!b;n;c\\          value: '"'"'${imageTag}'"'"'' ${manifestFile}
            
            echo "Changes made to ${manifestFile}:"
            git diff ${manifestFile}
            
            # Commit and push
            git config --global user.email "jenkins@ci.local"
            git config --global user.name "Jenkins CI"
            git add ${manifestFile}
            
            if ! git diff-index --quiet HEAD; then
                echo "Committing GitOps manifest updates..."
                git commit -m "ci: Update ${environment} image tags to build ${imageTag}"
                
                echo "Pushing to GitOps repository..."
                git push origin main
                
                echo "✅ GitOps manifest updated successfully"
            else
                echo "No changes to commit"
            fi
            
            cd ..
            rm -rf temp_gitops_repo
        """
    }
}

