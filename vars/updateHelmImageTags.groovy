def call(Map config) {
    echo "📝 Updating Helm values with new image tags..."
    
    def imageTag = config.imageTag ?: env.BUILD_NUMBER
    def helmValuesFile = config.helmValuesFile ?: 'helm-charts/todo-app/values-prod.yaml'
    def services = config.services ?: []
    
    def gitPushCredentialId = config.gitPushCredentialId ?: 'github-webhook'
    
    withCredentials([
        usernamePassword(credentialsId: gitPushCredentialId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')
    ]) {
        sh """
            echo "Updating image tags in ${helmValuesFile}..."
            
            # Update frontend image tag
            sed -i '/^frontend:/,/^[^ ]/ s/tag: .*/tag: "${imageTag}"/' ${helmValuesFile}
            
            # Update user-service image tag
            sed -i '/^userService:/,/^[^ ]/ s/tag: .*/tag: "${imageTag}"/' ${helmValuesFile}
            
            # Update todo-service image tag
            sed -i '/^todoService:/,/^[^ ]/ s/tag: .*/tag: "${imageTag}"/' ${helmValuesFile}
            
            # Show changes
            echo "Changes made to ${helmValuesFile}:"
            git diff ${helmValuesFile}
            
            # Commit and push
            git config --global user.email "jenkins@ci.local"
            git config --global user.name "Jenkins CI"
            git add ${helmValuesFile}
            
            if ! git diff-index --quiet HEAD; then
                echo "Committing image tag updates..."
                git commit -m "ci: Update image tags to build ${imageTag}"
                
                # Get current branch
                CURRENT_BRANCH=\$(git rev-parse --abbrev-ref HEAD)
                echo "Pushing to branch: \$CURRENT_BRANCH"
                
                # Push using credentials
                git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/KeremAR/proxmox-k3s.git HEAD:\$CURRENT_BRANCH
            else
                echo "No changes to commit"
            fi
        """
    }
}

