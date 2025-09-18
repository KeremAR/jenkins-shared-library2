/**
 * Deploys the production environment using ArgoCD.
 * This function updates the application's target revision to the current Git tag and syncs it.
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - argoCdCredentialId: The ID of the Jenkins secret text credential for the ArgoCD auth token.
 *   - argoCdProdAppName: The name of the ArgoCD application for production.
 */
def call(Map config) {
    ensureArgoCD()
    echo "🚀 Triggering ArgoCD sync for production from tag ${env.TAG_NAME}..."
    withCredentials([string(credentialsId: config.argoCdCredentialId, variable: 'ARGOCD_AUTH_TOKEN')]) {
        sh "./argocd login ${env.ARGOCD_SERVER} --auth-token=${ARGOCD_AUTH_TOKEN} --insecure"
        sh "./argocd app set ${config.argoCdProdAppName} --revision ${env.TAG_NAME}"
        sh "./argocd app sync ${config.argoCdProdAppName}"
        sh "./argocd app wait ${config.argoCdProdAppName} --health --timeout 600"
    }
}
