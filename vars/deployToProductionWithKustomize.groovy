#!/usr/bin/env groovy

/**
 * Handles the deployment to the production environment using Kustomize.
 * It promotes the 'latest' image by re-tagging and pushing it with the new version tag,
 * updates the Kustomize overlay, and then calls deployWithKustomize.
 *
 * @param config A map containing the project configuration. Requires:
 *               - services (list of service maps, each with a 'name')
 *               - registry
 *               - username
 *               - appName
 *               - registryCredentialsId
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.services || !config.registryCredentialsId || !config.registry || !config.username || !config.appName) {
        error("Missing required parameters: 'services', 'registryCredentialsId', 'registry', 'username', and 'appName' must be provided.")
    }
    if (!env.TAG_NAME) {
        error("This function should only be run on a Git tag. No TAG_NAME found.")
    }

    // --- Prepare Production Deployment ---
    def productionImageTag = env.TAG_NAME.replace('v', '')
    echo "🚀 Promoting to version '${productionImageTag}' for Production Environment (Kustomize)..."

    container('docker') {
        withCredentials([usernamePassword(credentialsId: config.registryCredentialsId, passwordVariable: 'REGISTRY_PASSWORD', usernameVariable: 'REGISTRY_USERNAME')]) {
            sh "echo \$REGISTRY_PASSWORD | docker login ${config.registry} -u \$REGISTRY_USERNAME --password-stdin"

            // For each service, pull the 'latest' image, re-tag it with the production version, and push the new tag.
            config.services.each { service ->
                def imageName = "${config.registry}/${config.username}/${config.appName}-${service.name}"
                def latestImage = "${imageName}:latest"
                def productionImage = "${imageName}:${productionImageTag}"

                echo "--- Promoting service: ${service.name} ---"
                echo "1. Pulling latest tested image: ${latestImage}"
                sh "docker pull ${latestImage}"

                echo "2. Re-tagging for production: ${productionImage}"
                sh "docker tag ${latestImage} ${productionImage}"

                echo "3. Pushing production image: ${productionImage}"
                sh "docker push ${productionImage}"
                echo "-------------------------------------------"
            }
        }
    }

    // --- Update Kustomize Configuration ---
    def overlayPath = 'kustomize/overlays/production'
    container('docker') {
        echo "🔧 Installing Kustomize..."
        sh '''
            apk add --no-cache curl bash
            curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh"  | bash
            mv kustomize /usr/local/bin/
        '''

        dir(overlayPath) {
            echo "Updating image tags in Kustomize overlay: ${overlayPath}"
            config.services.each { service ->
                def imageName = "${config.registry}/${config.username}/${config.appName}-${service.name}"
                def imageWithTag = "${imageName}:${productionImageTag}"
                
                echo "Setting image for ${service.name} to ${imageWithTag}"
                sh "kustomize edit set image ${imageName}=${imageWithTag}"
            }
        }
    }

    // --- Execute Deployment ---
    echo "🚢 Deploying version '${productionImageTag}' to Kubernetes with Kustomize..."
    deployWithKustomize(
        overlayPath: overlayPath
    )
}
