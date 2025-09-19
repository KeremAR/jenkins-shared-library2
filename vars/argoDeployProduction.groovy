/**
 * Deploys the production environment using ArgoCD.
 * This function updates the application's target revision to the current Git tag and syncs it.
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - argoCdUserCredentialId: The ID of the Jenkins secret text credential for the ArgoCD username.
 *   - argoCdPassCredentialId: The ID of the Jenkins secret text credential for the ArgoCD password.
 *   - argoCdProdAppName: The name of the ArgoCD application for production.
 */
def call(Map config) {
    ensureArgoCD()
    echo "🚀 Triggering ArgoCD sync for production from tag ${env.TAG_NAME}..."
    
    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'
    def gitPushCredentialId = config.gitPushCredentialId ?: 'github-webhook' // Git'e push yapmak için credential
    def repoUrl = config.repoUrl ?: 'github.com/KeremAR/todo-app-gitops' // HTTPS repo URL'si

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
            sh '''
                echo "Updating manifest file..."
                # 'g' flag'i olmadan sed kullanarak sadece ilk bulduğunu değiştir
                sed -i "s|targetRevision: '.*'|targetRevision: '${GIT_TAG_NAME}'|" argocd-manifests/environments/production.yaml

                echo "Pushing manifest changes to Git..."
                git config --global user.email "jenkins@local-devops-infrastructure.com"
                git config --global user.name "Jenkins CI"
                git add argocd-manifests/environments/production.yaml
                git commit -m "ci: Update production targetRevision to ${GIT_TAG_NAME}"
                git push "https://${GIT_USERNAME}:${GIT_PASSWORD}@${repoUrl}" HEAD:main

                echo "Syncing ArgoCD application..."
                ./argocd login $ARGOCD_SERVER --username $ARGOCD_USERNAME --password $ARGOCD_PASSWORD --insecure --grpc-web
                # Değişikliğin Git'ten okunması için refresh et
                ./argocd app refresh $ARGO_APP_NAME
                ./argocd app sync $ARGO_APP_NAME
                ./argocd app wait $ARGO_APP_NAME --health --timeout 600
            '''
        }
    }
}
