#!/usr/bin/env groovy

/**
 * Handles the deployment to the production environment.
 * It promotes the 'latest' image by re-tagging and pushing it with the new version tag,
 * then calls deployWithHelm.
 *
 * @param config A map containing the project configuration. Requires:
 *               - helmReleaseName
 *               - helmChartPath
 *               - helmDockerConfigJsonCredentialsId
 *               - services (list of service maps, each with a 'name')
 *               - registry
 *               - username
 *               - appName
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.helmReleaseName || !config.helmChartPath || !config.helmDockerConfigJsonCredentialsId || !config.services) {
        error("Missing required parameters: 'helmReleaseName', 'helmChartPath', 'helmDockerConfigJsonCredentialsId', and 'services' must be provided.")
    }
    if (!env.TAG_NAME) {
        error("This function should only be run on a Git tag. No TAG_NAME found.")
    }

    // --- User Confirmation ---
    //input message: "Deploy to Production Environment? (Tag: ${env.TAG_NAME})", ok: 'Deploy'

    // --- Prepare Production Deployment ---
    def productionImageTag = env.TAG_NAME.replace('v', '')
    echo "🚀 Promoting to version '${productionImageTag}' for Production Environment..."

    container('docker') {
        withCredentials([string(credentialsId: 'github-registry', variable: 'REGISTRY_TOKEN'), string(credentialsId: 'github-username', variable: 'REGISTRY_USER')]) {
            sh "echo \$REGISTRY_TOKEN | docker login ghcr.io -u \$REGISTRY_USER --password-stdin"

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

    // --- Execute Deployment ---
    echo "🚢 Deploying version '${productionImageTag}' to Kubernetes..."
    deployWithHelm(
        releaseName: "${config.helmReleaseName}-prod",
        chartPath: config.helmChartPath,
        namespace: 'production',
        valuesFile: 'helm-charts/todo-app/values-prod.yaml',
        imageTag: productionImageTag,
        dockerConfigJsonCredentialsId: config.helmDockerConfigJsonCredentialsId
    )
}
