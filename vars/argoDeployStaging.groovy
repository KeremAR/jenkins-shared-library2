/**
 * Deploys the staging environment using ArgoCD.
 *
 * @param config A map containing the pipeline configuration.
 * Expected keys:
 *   - argoCdCredentialId: The ID of the Jenkins secret text credential for the ArgoCD auth token.
 *   - argoCdStagingAppName: The name of the ArgoCD application for staging.
 */
def call(Map config) {
    container('argocd') {
        script {
            echo "🚀 Triggering ArgoCD sync for staging environment..."
            withCredentials([string(credentialsId: config.argoCdCredentialId, variable: 'ARGOCD_AUTH_TOKEN')]) {
                sh "argocd login ${env.ARGOCD_SERVER} --auth-token=${ARGOCD_AUTH_TOKEN} --insecure"
                sh "argocd app sync ${config.argoCdStagingAppName} --refresh"
                sh "argocd app wait ${config.argoCdStagingAppName} --health --timeout 300"
            }
        }
    }
}
