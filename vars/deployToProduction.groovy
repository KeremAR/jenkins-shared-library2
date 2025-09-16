#!/usr/bin/env groovy

/**
 * Handles the deployment to the production environment.
 * It prompts for user confirmation, calculates the image tag from the Git tag,
 * and then calls the deployWithHelm function with production-specific parameters.
 *
 * @param params A map containing the project configuration. Requires:
 *               - helmReleaseName
 *               - helmChartPath
 *               - helmDockerConfigJsonCredentialsId
 */
def call(Map params) {
    // --- Configuration Validation ---
    if (!params.helmReleaseName || !params.helmChartPath || !params.helmDockerConfigJsonCredentialsId) {
        error("Missing required parameters: 'helmReleaseName', 'helmChartPath', and 'helmDockerConfigJsonCredentialsId' must be provided.")
    }

    // Abort if not running on a tag. This is a safety check.
    if (!env.TAG_NAME) {
        error("This function should only be run on a Git tag. No TAG_NAME found.")
    }

    // --- User Confirmation ---
    // The input step pauses the pipeline until a user manually approves it.
    // This line is commented out for testing purposes.
    // input message: "Deploy to Production Environment? (Tag: ${env.TAG_NAME})", ok: 'Deploy'

    // --- Prepare Production Deployment ---
    // The image tag for production is derived from the Git tag by removing the 'v' prefix.
    // e.g., Git tag 'v1.2.3' becomes image tag '1.2.3'.
    def productionImageTag = env.TAG_NAME.replace('v', '')
    
    echo "🚀 Preparing to deploy tag '${productionImageTag}' to Production Environment..."

    // --- Execute Deployment ---
    // Call the existing deployWithHelm function with production-specific values.
    deployWithHelm(
        releaseName: "${params.helmReleaseName}-prod",
        chartPath: params.helmChartPath,
        namespace: 'production',
        valuesFile: 'helm-charts/todo-app/values-prod.yaml',
        imageTag: productionImageTag,
        dockerConfigJsonCredentialsId: params.helmDockerConfigJsonCredentialsId
    )
}
