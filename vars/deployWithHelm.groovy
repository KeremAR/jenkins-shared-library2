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

    container('docker') {
        echo "🔧 Installing Helm..."
        sh '''
            apk add --no-cache curl bash
            curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
            chmod 700 get_helm.sh
            ./get_helm.sh
        '''

        echo "🚀 Deploying with Helm..."
        
        // Construct the Helm command
        def helmCmd = "helm upgrade --install ${releaseName} ${chartPath} --namespace ${namespace} --create-namespace --wait --timeout=5m"

        // Add values file if provided
        if (valuesFile) {
            helmCmd += " -f ${valuesFile}"
        }

        // Set image tag if provided
        if (imageTag) {
            // This is a common pattern, but might need adjustment based on the chart's values structure.
            // e.g., --set image.tag, --set frontend.image.tag, etc.
            // For now, we assume a global image.tag
            helmCmd += " --set image.tag=${imageTag}"
        }
        
        echo "Executing Helm command: ${helmCmd}"
        sh helmCmd
        
        echo "✅ Helm deployment for release '${releaseName}' completed successfully!"
    }
}
