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

            echo "pre-flight checks..."
            // Step 1: Ensure the namespace exists.
            sh "kubectl create namespace ${namespace} || echo 'Namespace ${namespace} already exists.'"

            // Step 2: Apply the required RoleBinding for Jenkins to the namespace. This is idempotent.
            def rbacYaml = """
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: jenkins-deploy-binding-${namespace}
  namespace: ${namespace}
subjects:
- kind: ServiceAccount
  name: jenkins
  namespace: jenkins
roleRef:
  kind: ClusterRole
  name: admin
  apiGroup: rbac.authorization.k8s.io
"""
            sh "echo '${rbacYaml}' | kubectl apply -f -"


            echo "🚀 Deploying with Helm..."
            
            // Construct the Helm command WITHOUT --create-namespace
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
            
            def commandToRun = helmCmd
            if (dockerConfigJsonCredentialsId) {
                // By using single quotes for the script, we prevent Groovy from interpolating the secret.
                // The shell itself will safely substitute the environment variable. This avoids the Jenkins warning.
                sh "helm upgrade --install ${releaseName} ${chartPath} --namespace ${namespace} --wait --timeout=5m -f ${valuesFile} --set image.tag=${imageTag} --set global.imagePullSecrets.dockerconfigjson='\$DOCKER_CONFIG_JSON_B64'"
            } else {
                sh commandToRun
            }
            
            echo "✅ Helm deployment for release '${releaseName}' completed successfully!"
        }
    }
}
