#!/usr/bin/env groovy

/**
 * Get Changed Services
 * 
 * Detects which services have changed compared to the main branch.
 * This is used for feature branch optimization - only test/build what changed.
 * 
 * Features:
 * - Fetches main branch for comparison
 * - Uses git diff to find changed files
 * - Maps changed files to services
 * - Returns list of changed services
 * 
 * Usage:
 *   def changedServices = getChangedServices(services: config.services)
 *   if (changedServices.isEmpty()) {
 *       echo "No service changes detected"
 *   }
 * 
 * @param services List of service configurations with 'name' field
 * @return List of services that have changes
 */
def call(Map config) {
    def services = config.services
    
    // Fetch main branch to compare against (git is available in Jenkins agent)
    echo "🔍 Fetching main branch for comparison..."
    sh """
        git fetch origin main || echo "Already fetched"
    """
    
    // Get list of changed files
    echo "🔍 Detecting changed files..."
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
            echo "✓ Service '${serviceName}' has changes"
        }
    }
    
    if (changedServices.isEmpty()) {
        echo "⚠️ No service changes detected."
        echo "Changed files were:\n${changedFiles}"
        echo "This might be a documentation-only change or infrastructure change."
    } else {
        echo "📋 Changed services: ${changedServices.collect { it.name }.join(', ')}"
    }
    
    return changedServices
}

