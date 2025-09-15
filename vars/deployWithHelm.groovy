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
            echo "🔧 Installing Helm..."
            sh '''
                apk add --no-cache curl bash
                curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
                chmod 700 get_helm.sh
                ./get_helm.sh
            '''

            echo "🚀 Deploying with Helm..."
            
            // The pipeline will now create the namespace via Helm. This is the simplest and most reliable approach.
            def helmCmd = "helm upgrade --install ${releaseName} ${chartPath} --namespace ${namespace} --create-namespace --wait --timeout=5m"

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
                sh "${helmCmd} --set global.imagePullSecrets.dockerconfigjson='\$DOCKER_CONFIG_JSON_B64'"
            } else {
                sh helmCmd
            }
            
            echo "✅ Helm deployment for release '${releaseName}' completed successfully!"
        }
    }
}
