/**
 * Deploys the staging environment using ArgoCD.
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - argoCdUserCredentialId: The ID of the Jenkins secret text credential for the ArgoCD username.
 *   - argoCdPassCredentialId: The ID of the Jenkins secret text credential for the ArgoCD password.
 *   - argoCdStagingAppName: The name of the ArgoCD application for staging.
 */
def call(Map config) {
    ensureArgoCD()
    echo "🚀 Triggering ArgoCD sync for staging environment..."

    def userCredentialId = config.argoCdUserCredentialId ?: 'argocd-username'
    def passCredentialId = config.argoCdPassCredentialId ?: 'argocd-password'

    withCredentials([
        string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
        string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
    ]) {
        withEnv([
            "ARGOCD_SERVER=${env.ARGOCD_SERVER}",
            "ARGO_APP_NAME=${config.argoCdStagingAppName}"
        ]) {
            sh '''
                ./argocd login $ARGOCD_SERVER --username $ARGOCD_USERNAME --password $ARGOCD_PASSWORD --insecure --grpc-web --core
                ./argocd app sync $ARGO_APP_NAME --refresh
                ./argocd app wait $ARGO_APP_NAME --health --timeout 300
            '''
        }
    }
}
