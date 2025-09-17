#!/usr/bin/env groovy

/**
 * Deploys resources to Kubernetes using Kustomize.
 *
 * @param config A map containing the configuration for the Kustomize deployment.
 *               - overlayPath (required): The path to the Kustomize overlay directory.
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.overlayPath) {
        error("Missing required parameter: 'overlayPath' must be provided.")
    }

    def overlayPath = config.overlayPath

    container('docker') {
        echo "🔧 Installing kubectl..."
        sh '''
            apk add --no-cache curl
            curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
            chmod +x kubectl
            mv kubectl /usr/local/bin/
        '''

        echo "🚀 Deploying with Kustomize from path: ${overlayPath}..."

        // Using kubectl to apply the kustomization.
        // --wait: Waits for all resources to be in a ready state.
        sh "kubectl apply -k ${overlayPath} --wait"

        echo "✅ Kustomize deployment from '${overlayPath}' completed successfully!"
    }
}
