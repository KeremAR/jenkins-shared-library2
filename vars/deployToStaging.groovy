#!/usr/bin/env groovy

/**
 * Deploys the application to the staging environment.
 * This function is a wrapper around deployWithHelm, providing staging-specific parameters.
 *
 * @param config A map containing the project configuration. Requires:
 *               - helmReleaseName
 *               - helmChartPath
 *               - helmDockerConfigJsonCredentialsId
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.helmReleaseName || !config.helmChartPath || !config.helmDockerConfigJsonCredentialsId) {
        error("Missing required parameters: 'helmReleaseName', 'helmChartPath', and 'helmDockerConfigJsonCredentialsId' must be provided.")
    }

    if (!env.IMAGE_TAG) {
        error("IMAGE_TAG environment variable not set. This function requires a built image tag.")
    }

    echo "⚡ Deploying image tag '${env.IMAGE_TAG}' to Staging Environment..."

    deployWithHelm(
        releaseName: "${config.helmReleaseName}-staging",
        chartPath: config.helmChartPath,
        namespace: 'staging',
        valuesFile: 'helm-charts/todo-app/values-staging.yaml',
        imageTag: env.IMAGE_TAG,
        dockerConfigJsonCredentialsId: config.helmDockerConfigJsonCredentialsId
    )
}
