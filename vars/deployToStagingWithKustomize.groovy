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
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.services || !config.registry || !config.username || !config.appName) {
        error("Missing required parameters: 'services', 'registry', 'username', and 'appName' must be provided.")
    }
    if (!env.IMAGE_TAG) {
        error("IMAGE_TAG environment variable not set. This function requires a built image tag.")
    }

    echo "⚡ Deploying image tag '${env.IMAGE_TAG}' to Staging Environment with Kustomize..."

    def overlayPath = 'kustomize/overlays/staging'

    container('docker') {
        echo "🔧 Installing Kustomize..."
        sh '''
            apk add --no-cache curl bash
            (cd /tmp && curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash)
            mv /tmp/kustomize /usr/local/bin/
        '''

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

    // --- Execute Deployment ---
    deployWithKustomize(
        overlayPath: overlayPath
    )
}
