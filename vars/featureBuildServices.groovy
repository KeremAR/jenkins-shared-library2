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
    
    echo "🔍 Feature branch detected - building only changed services..."
    
    // Fetch main branch to compare against (outside container, git is available here)
    echo "Fetching main branch for comparison..."
    sh """
        git fetch origin main || echo "Already fetched"
    """
    
    // Get list of changed files (outside container, git is available here)
    echo "Detecting changed files..."
    def changedFiles = sh(
        script: """
            # Try different methods to get changed files
            if git diff --name-only origin/main HEAD 2>/dev/null; then
                exit 0
            elif git diff --name-only FETCH_HEAD HEAD 2>/dev/null; then
                exit 0
            else
                # Fallback: compare with previous commit
                git diff --name-only HEAD~1 2>/dev/null || echo ""
            fi
        """,
        returnStdout: true
    ).trim()
    
    echo "Changed files:\n${changedFiles}"
    
    // Determine which services have changed
    def changedServices = []
    services.each { service ->
        def serviceName = service.name
        // Check if any file in the service directory was changed
        if (changedFiles.contains("${serviceName}/")) {
            changedServices.add(service)
            echo "✓ Service '${serviceName}' has changes and will be built"
        }
    }
    
    if (changedServices.isEmpty()) {
        echo "⚠️ No service changes detected. Skipping build."
        echo "Changed files were:\n${changedFiles}"
        echo "This might be a documentation-only change or infrastructure change."
        return []
    }
    
    echo "📋 Building ${changedServices.size()} changed service(s): ${changedServices.collect { it.name }.join(', ')}"
    
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

