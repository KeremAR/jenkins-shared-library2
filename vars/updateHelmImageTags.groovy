def call(Map config) {
    echo "📝 Updating Helm values with new image tags..."
    
    def imageTag = config.imageTag ?: env.BUILD_NUMBER
    def helmValuesFile = config.helmValuesFile ?: 'helm-charts/todo-app/values-prod.yaml'
    def services = config.services ?: []
    def targetBranch = config.targetBranch ?: 'main'
    
    def gitPushCredentialId = config.gitPushCredentialId ?: 'github-webhook'
    
    // Use Jenkins BRANCH_NAME if available, fallback to config
    def branchToPush = env.BRANCH_NAME ?: targetBranch
    
    withCredentials([
        usernamePassword(credentialsId: gitPushCredentialId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')
    ]) {
        withEnv(["TARGET_BRANCH=${branchToPush}"]) {
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
                git commit -m "ci: Update image tags to build ${imageTag} [skip ci]"
                
                # Push to target branch (Jenkins uses detached HEAD, so we push directly to branch)
                echo "Pushing to branch: \${TARGET_BRANCH}"
                git push https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/KeremAR/proxmox-k3s.git HEAD:refs/heads/\${TARGET_BRANCH}
                
                echo "✅ Image tags updated and pushed (CI skip enabled)"
            else
                echo "No changes to commit"
            fi
            """
        }
    }
}

