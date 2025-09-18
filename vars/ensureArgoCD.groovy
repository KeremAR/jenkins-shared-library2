/**
 * Ensures that the ArgoCD CLI is available in the current workspace.
 * If not found, it downloads a specific version of the CLI and makes it executable.
 */
def call() {
    sh '''
        if [ ! -f ./argocd ]; then
            echo "ArgoCD CLI not found in workspace. Downloading..."
            curl -SL -o argocd-linux-amd64 https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
            chmod +x ./argocd
            echo "ArgoCD CLI downloaded successfully."
        else
            echo "ArgoCD CLI already exists in workspace."
        fi
    '''
}
