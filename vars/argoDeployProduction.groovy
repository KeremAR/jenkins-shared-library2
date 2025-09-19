def call(Map config) {
    ensureArgoCD()
    echo "🚀 Triggering ArgoCD sync for production from tag ${env.TAG_NAME}..."
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'
    def gitPushCredentialId = config.gitPushCredentialId ?: 'github-webhook'
    def repoUrl = config.repoUrl ?: 'github.com/KeremAR/todo-app-gitops'

    withCredentials([
        string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
        string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD'),
        usernamePassword(credentialsId: gitPushCredentialId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')
    ]) {
        withEnv([
            "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
            "ARGO_APP_NAME=${config.argoCdProdAppName}",
            "GIT_TAG_NAME=${env.TAG_NAME}"
        ]) {
            // DEĞİŞİKLİK BURADA: Tek tırnak yerine çift tırnak kullanılıyor.
            sh """
                echo "Cloning manifest repository to update it..."
                # Groovy'nin ${repoUrl} değişkenini işlemesi için çift tırnak kullanıldı.
                git clone "https://${GIT_USERNAME}:${GIT_PASSWORD}@${repoUrl}.git" temp_gitops_repo
                cd temp_gitops_repo

                echo "Updating manifest file..."
                sed -i "s|targetRevision: '.*'|targetRevision: '${GIT_TAG_NAME}'|" argocd-manifests/environments/production.yaml

                echo "Pushing manifest changes to Git..."
                git config --global user.email "jenkins@local-devops-infrastructure.com"
                git config --global user.name "Jenkins CI"
                git add argocd-manifests/environments/production.yaml
                git commit -m "ci: Update production targetRevision to ${GIT_TAG_NAME}"
                git push origin HEAD:main

                cd ..

                echo "Syncing ArgoCD application..."
                ./argocd login \$ARGOCD_SERVER --username \$ARGOCD_USERNAME --password \$ARGOCD_PASSWORD --insecure --grpc-web
                ./argocd app refresh \$ARGO_APP_NAME
                ./argocd app sync \$ARGO_APP_NAME
                ./argocd app wait \$ARGO_APP_NAME --health --timeout 600
            """
        }
    }
}