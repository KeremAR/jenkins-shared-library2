def call(Map config) {
    echo "🚀 Triggering ArgoCD sync for production from commit to main branch ${env.GIT_COMMIT}..."
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'
    def gitPushCredentialId = config.gitPushCredentialId ?: 'github-webhook'
    def repoUrl = config.repoUrl 

    container('argo') {
        withCredentials([
            string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
            string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD'),
            usernamePassword(credentialsId: gitPushCredentialId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')
        ]) {
            withEnv([
                "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
                "ARGO_APP_NAME=${config.argoCdProdAppName}",
                "GIT_REVISION=${env.GIT_COMMIT}"
            ]) {
                // DEĞİŞİKLİK BURADA: Tek tırnak yerine çift tırnak kullanılıyor.
                sh """
                    echo "Cloning manifest repository to update it..."
                    # Groovy'nin ${repoUrl} değişkenini işlemesi için çift tırnak kullanıldı.
                    git clone "https://${GIT_USERNAME}:${GIT_PASSWORD}@${repoUrl}.git" temp_gitops_repo
                    cd temp_gitops_repo

                    echo "Updating manifest file..."
                    sed -i "s|targetRevision: .*|targetRevision: ${GIT_REVISION} |" argocd-manifests/environments/production.yaml

                    echo "Pushing manifest changes to Git..."
                    git config --global user.email "jenkins@local-devops-infrastructure.com"
                    git config --global user.name "Jenkins CI"
                    git add argocd-manifests/environments/production.yaml
                    if ! git diff-index --quiet HEAD; then
                        echo "Changes found, committing and pushing..."
                        git commit -m "ci: Update production targetRevision to ${GIT_REVISION}"
                        git push origin HEAD:main
                    else
                        echo "No changes in targetRevision. Git repository is already up to date."
                    fi

                    cd ..
                    rm -rf temp_gitops_repo
                    
                    echo "Syncing ArgoCD application..."
                    argocd login \$ARGOCD_SERVER --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                    argocd app sync \$ARGO_APP_NAME
                    argocd app wait \$ARGO_APP_NAME --health --timeout 600
                """
            }
        }
    }
}