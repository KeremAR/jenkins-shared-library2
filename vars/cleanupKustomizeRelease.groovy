#!/usr/bin/env groovy

/**
 * Deletes all Kubernetes resources defined in a Kustomize overlay.
 *
 * @param config A map containing the configuration.
 *               - overlayPath (required): The path to the Kustomize overlay directory.
 *               - namespace (required): The namespace from which to delete the resources.
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.overlayPath || !config.namespace) {
        error("Missing required parameters: 'overlayPath' and 'namespace' must be provided.")
    }

    def overlayPath = config.overlayPath
    def namespace = config.namespace

    container('docker') {
        echo "🔧 Installing kubectl..."
        sh '''
            apk add --no-cache curl
            curl -L "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" -o /tmp/kubectl
            chmod +x /tmp/kubectl
            mv /tmp/kubectl /usr/local/bin/
        '''

        echo "🧹 Cleaning up Kustomize resources from namespace '${namespace}' using overlay '${overlayPath}'..."
        
        // --ignore-not-found=true ensures the command doesn't fail if some resources are already deleted.
        sh "kubectl delete -k ${overlayPath} --namespace ${namespace} --ignore-not-found=true"

        echo "✅ Cleanup of Kustomize resources completed."
    }
}
