#!/usr/bin/env groovy

/**
 * Deploys the application to the staging environment using Kustomize.
 * It sets the image tag for all services and then calls deployWithKustomize.
 *
 * @param config A map containing the project configuration. Requires:
 *               - services (list of service maps, each with a 'name')
 *               - registry
 *               - username
 *               - appName
 *               - dockerConfigJsonCredentialsId (required)
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.services || !config.registry || !config.username || !config.appName || !config.dockerConfigJsonCredentialsId) {
        error("Missing required parameters: 'services', 'registry', 'username', 'appName', and 'dockerConfigJsonCredentialsId' must be provided.")
    }
    if (!env.IMAGE_TAG) {
        error("IMAGE_TAG environment variable not set. This function requires a built image tag.")
    }

    echo "⚡ Deploying image tag '${env.IMAGE_TAG}' to Staging Environment with Kustomize..."

    def overlayPath = 'kustomize/overlays/staging'
    def namespace = 'staging'
    def secretName = 'github-registry-secret'

    withCredentials([string(credentialsId: config.dockerConfigJsonCredentialsId, variable: 'DOCKER_CONFIG_JSON_B64')]) {
        container('docker') {
            echo "🔧 Installing Kustomize & kubectl..."
            sh '''
                apk add --no-cache curl bash
                (cd /tmp && curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash)
                mv /tmp/kustomize /usr/local/bin/
                
                curl -L "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" -o /tmp/kubectl
                chmod +x /tmp/kubectl
                mv /tmp/kubectl /usr/local/bin/
            '''

            echo "Ensuring namespace '${namespace}' exists..."
            sh "kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -"

            echo "🔐 Creating/updating image pull secret '${secretName}' in namespace '${namespace}'..."
            sh """
                kubectl delete secret ${secretName} --namespace ${namespace} --ignore-not-found
                kubectl create secret generic ${secretName} \\
                    --namespace ${namespace} \\
                    --from-literal=.dockerconfigjson="\$(echo \$DOCKER_CONFIG_JSON_B64 | base64 -d)" \\
                    --type=kubernetes.io/dockerconfigjson
            """

            // Change to the overlay directory to update the kustomization.yaml file
            dir(overlayPath) {
                echo "Updating image tags in Kustomize overlay: ${overlayPath}"
                config.services.each { service ->
                    def imageName = "${config.registry}/${config.username}/${config.appName}-${service.name}"
                    def imageWithTag = "${imageName}:${env.IMAGE_TAG}"
                    
                    echo "Setting image for ${service.name} to ${imageWithTag}"
                    // The syntax is image_name=new_image_name:new_tag. Here we only change the tag.
                    sh "kustomize edit set image ${imageName}=${imageWithTag}"
                }
            }
        }
    }

    // --- Execute Deployment ---
    deployWithKustomize(
        overlayPath: overlayPath
    )
}
