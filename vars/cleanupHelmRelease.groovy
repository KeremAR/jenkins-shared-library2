#!/usr/bin/env groovy

/**
 * Uninstalls a Helm release from a specific namespace.
 * This is useful for cleaning up environments to free up resources.
 *
 * @param config A map containing the configuration for the Helm cleanup.
 *               - releaseName (required): The name of the Helm release to uninstall.
 *               - namespace (required): The Kubernetes namespace from which the release will be uninstalled.
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.releaseName || !config.namespace) {
        error("Missing required parameters: 'releaseName' and 'namespace' must be provided for cleanup.")
    }

    def releaseName = config.releaseName
    def namespace = config.namespace

    echo "🧹 Cleaning up Helm release '${releaseName}' in namespace '${namespace}'..."
    
    container('docker') {
        // This stage might run independently via a tag, so we must ensure Helm is installed.
        sh '''
            if ! command -v helm &> /dev/null; then
                echo "🔧 Helm not found. Installing..."
                apk add --no-cache curl bash
                curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
                chmod 700 get_helm.sh
                ./get_helm.sh
            else
                echo "✅ Helm is already installed."
            fi
        '''

        // 'helm uninstall' command gracefully handles cases where the release does not exist,
        // preventing the pipeline from failing unnecessarily.
        sh "helm uninstall ${releaseName} --namespace ${namespace}"
        
        echo "✅ Cleanup for release '${releaseName}' completed successfully."
    }
}
