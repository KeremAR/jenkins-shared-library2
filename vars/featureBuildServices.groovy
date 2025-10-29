#!/usr/bin/env groovy

/**
 * Feature Branch Build Services
 * 
 * This function builds ONLY services that have changed compared to the main branch.
 * This speeds up feedback for developers on feature branches.
 * 
 * Features:
 * - Detects changed services via git diff against main branch
 * - Builds only changed services
 * - Returns list of built images
 * - Fast feedback loop
 * 
 * Usage:
 *   def images = featureBuildServices(
 *       services: config.services,
 *       registry: config.registry,
 *       username: config.username,
 *       imageTag: env.IMAGE_TAG,
 *       appName: config.appName
 *   )
 */
def call(Map config) {
    def services = config.services
    def registry = config.registry
    def username = config.username
    def imageTag = config.imageTag
    def appName = config.appName
    
    echo "🔨 Feature branch detected - building only changed services..."
    
    // Use shared utility to detect changed services
    def changedServices = getChangedServices(services: services)
    
    if (changedServices.isEmpty()) {
        echo "⚠️ Skipping build - no service changes detected."
        return []
    }
    
    echo "📋 Building ${changedServices.size()} changed service(s)"
    
    def builtImages = []
    
    // Build changed services in parallel (using existing buildDockerImage library)
    def parallelBuilds = [:]
    
    changedServices.each { service ->
        parallelBuilds["Build ${service.name}"] = {
            def buildConfig = [
                serviceName: service.name,
                dockerfilePath: service.dockerfile,
                contextPath: service.context ?: '.',
                registry: registry,
                username: username,
                imageTag: imageTag,
                appName: appName
            ]
            
            def images = buildDockerImage(buildConfig)
            builtImages.addAll([images.versioned, images.latest])
        }
    }
    
    parallel parallelBuilds
    
    echo "🎉 Successfully built ${builtImages.size()} image(s)!"
    return builtImages
}

