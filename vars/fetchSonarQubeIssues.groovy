#!/usr/bin/env groovy

/**
 * Fetches and displays SonarQube issues from the API.
 * 
 * @param config A map containing:
 *               - projectKey (required): The SonarQube project key
 *               - sonarUrl (required): The SonarQube server URL
 *               - sonarToken (required): The SonarQube authentication token
 *               - severities (optional): Comma-separated list of severities (default: "BLOCKER,CRITICAL,MAJOR")
 *               - statuses (optional): Comma-separated list of statuses (default: "OPEN,CONFIRMED")
 *               - maxIssues (optional): Maximum number of issues to fetch (default: 100)
 */
def call(Map config = [:]) {
    echo "🔍 DEBUG: fetchSonarQubeIssues called with projectKey: ${config.projectKey}"
    
    // --- Configuration Validation ---
    if (!config.projectKey) {
        echo "⚠️  projectKey not provided, skipping issue fetch"
        return
    }
    
    if (!config.sonarUrl || !config.sonarToken) {
        echo "⚠️  SonarQube credentials not provided, skipping issue fetch"
        return
    }

    def projectKey = config.projectKey
    def sonarUrl = config.sonarUrl
    def sonarToken = config.sonarToken
    def severities = config.severities ?: "BLOCKER,CRITICAL,MAJOR"
    def statuses = config.statuses ?: "OPEN,CONFIRMED"
    def maxIssues = config.maxIssues ?: 100

    echo "📋 Fetching SonarQube issues..."
    echo "🔍 DEBUG: About to enter argo container"
    
    container('argo') {
        echo "🔍 DEBUG: Inside argo container"
        try {
            
            // Get issues from SonarQube API
            echo "🔍 DEBUG: Running curl command..."
            def issuesJson = sh(
                script: """curl -s -u ${sonarToken}: \
                    '${sonarUrl}/api/issues/search?componentKeys=${projectKey}&severities=${severities}&statuses=${statuses}&ps=${maxIssues}'""",
                returnStdout: true
            ).trim()
            
            echo "🔍 DEBUG: Curl completed, response length: ${issuesJson?.length() ?: 0}"
            
            if (!issuesJson || issuesJson.isEmpty()) {
                echo "⚠️  Empty response from SonarQube API"
                return
            }
            
            echo "✅ Received response from SonarQube API (${issuesJson.length()} bytes)"
            
            // Parse and display issues
            echo "📊 Parsing JSON response..."
            def issues = readJSON text: issuesJson
            echo "✅ JSON parsed successfully"
            
            if (issues.issues && issues.issues.size() > 0) {
                echo "⚠️  Found ${issues.total} open issues (showing ${issues.issues.size()}):"
                echo "=" * 80
                
                issues.issues.each { issue ->
                    def fileName = issue.component.tokenize(':').last()
                    def lineNumber = issue.line ?: 'N/A'
                    
                    echo """
Type: ${issue.type} | Severity: ${issue.severity}
File: ${fileName}:${lineNumber}
Rule: ${issue.rule}
Message: ${issue.message}
${'-' * 80}"""
                }
                
                // Save issues to file
                writeFile file: 'sonarqube-issues.txt', text: issuesJson
                archiveArtifacts artifacts: 'sonarqube-issues.txt', allowEmptyArchive: true
                echo "📄 Issues saved to sonarqube-issues.txt (archived as artifact)"
                echo "💡 Download the artifact from Jenkins build page for full details"
            } else {
                echo "✅ No open issues found!"
            }
            
        } catch (Exception e) {
            echo "⚠️  Could not fetch SonarQube issues: ${e.message}"
            // Don't fail the build, just log the error
        }
    }
}

