#!/usr/bin/env groovy

/**
 * Deploys a Helm chart to Kubernetes.
 *
 * @param config A map containing the configuration for the Helm deployment.
 *               - releaseName (required): The name for the Helm release.
 *               - chartPath (required): The path to the Helm chart directory.
 *               - namespace (required): The Kubernetes namespace to deploy into.
 *               - valuesFile (optional): The path to a custom values.yaml file.
 *               - imageTag (optional): The image tag to set in the Helm chart.
 *               - dockerConfigJsonCredentialsId (optional): The ID of the Jenkins 'Secret text' credential containing the base64 docker config.
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.releaseName || !config.chartPath || !config.namespace) {
        error("Missing required parameters: 'releaseName', 'chartPath', and 'namespace' must be provided.")
    }

    def releaseName = config.releaseName
    def chartPath = config.chartPath
    def namespace = config.namespace
    def valuesFile = config.valuesFile
    def imageTag = config.imageTag
    def dockerConfigJsonCredentialsId = config.dockerConfigJsonCredentialsId

    withCredentials([string(credentialsId: dockerConfigJsonCredentialsId, variable: 'DOCKER_CONFIG_JSON_B64')]) {
        container('docker') {
            echo "🔧 Installing Helm & Kubectl..."
            sh '''
                apk add --no-cache curl bash
                curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
                chmod 700 get_helm.sh
                ./get_helm.sh

                curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl"
                chmod +x kubectl
                mv kubectl /usr/local/bin/
            '''

            echo "Pre-flight check: Preparing namespace for Helm..."
            // Add the required labels and annotations to the manually created namespace
            // so that Helm can install a new release into it. --overwrite makes this idempotent.
            sh "kubectl label namespace ${namespace} app.kubernetes.io/managed-by=Helm --overwrite"
            sh "kubectl annotate namespace ${namespace} meta.helm.sh/release-name=${releaseName} --overwrite"
            sh "kubectl annotate namespace ${namespace} meta.helm.sh/release-namespace=${namespace} --overwrite"


            echo "🚀 Deploying with Helm..."
            
            // Construct the Helm command. Assumes namespace is manually created.
            def helmCmd = "helm upgrade --install ${releaseName} ${chartPath} --namespace ${namespace} --wait --timeout=5m"

            // Add values file if provided
            if (valuesFile) {
                helmCmd += " -f ${valuesFile}"
            }

            // Set image tag if provided
            if (imageTag) {
                helmCmd += " --set image.tag=${imageTag}"
            }
            
            echo "Executing Helm command..." // We don't print the full command to avoid leaking the secret in logs
            
            if (dockerConfigJsonCredentialsId) {
                // By using single quotes for the script, we prevent Groovy from interpolating the secret.
                // The shell itself will safely substitute the environment variable. This avoids the Jenkins warning.
                sh "${helmCmd} --set global.imagePullSecrets.dockerconfigjson='\$DOCKER_CONFIG_JSON_B64'"
            } else {
                sh helmCmd
            }
            
            echo "✅ Helm deployment for release '${releaseName}' completed successfully!"
        }
    }
}
