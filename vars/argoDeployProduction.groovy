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

    withCredentials([
        string(credentialsId: userCredentialId, variable: 'ARGOCD_USERNAME'),
        string(credentialsId: passCredentialId, variable: 'ARGOCD_PASSWORD')
    ]) {
        withEnv(["ARGOCD_SERVER=${env.ARGOCD_SERVER}"]) {
            sh '''
                ./argocd login $ARGOCD_SERVER --username $ARGOCD_USERNAME --password $ARGOCD_PASSWORD --insecure --grpc-web --core
                ./argocd app set ${config.argoCdProdAppName} --revision ${env.TAG_NAME}
                ./argocd app sync ${config.argoCdProdAppName}
                ./argocd app wait ${config.argoCdProdAppName} --health --timeout 600
            '''
        }
    }
}
