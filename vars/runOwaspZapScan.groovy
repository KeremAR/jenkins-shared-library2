#!/usr/bin/env groovy

/**
 * Run OWASP ZAP Baseline Security Scan using Docker
 * 
 * This library runs OWASP ZAP baseline security scan against a target URL
 * using the official ZAP Docker image. The scan generates HTML, Markdown,
 * and JSON reports that are archived as Jenkins artifacts.
 * 
 * SCAN BEHAVIOR:
 * - Uses zap-baseline.py script (passive scan + spider)
 * - Runs with -l WARN flag: Alerts do NOT fail the build
 * - Generates 3 report formats: HTML, MD, JSON
 * - Reports are archived and published in Jenkins UI
 * 
 * DOCKER IMPLEMENTATION:
 * - Uses ghcr.io/zaproxy/zaproxy:stable image
 * - Mounts workspace directory for report output
 * - Container is automatically removed after scan (--rm flag)
 * 
 * @param config Map containing:
 *   - targetUrl: REQUIRED - URL to scan (e.g., 'http://my-app.staging.example.com')
 *   - zapImage: Docker image to use (default: 'ghcr.io/zaproxy/zaproxy:stable')
 *   - reportDir: Directory for reports (default: 'zap-reports')
 *   - scanLevel: Alert level threshold (default: 'WARN' - doesn't fail build)
 *   - timeout: Scan timeout in minutes (default: 30)
 * 
 * @example
 * runOwaspZapScan(
 *     targetUrl: 'http://staging.epam-proxmox-k3s',
 *     scanLevel: 'WARN',
 *     timeout: 30
 * )
 */
def call(Map config = [:]) {
    // Validate required parameters
    if (!config.targetUrl) {
        error("❌ targetUrl is required! Please provide the URL to scan.")
    }
    
    // Optional parameters with defaults
    def targetUrl = config.targetUrl
    def zapImage = config.zapImage ?: 'ghcr.io/zaproxy/zaproxy:stable'
    def reportDir = config.reportDir ?: 'zap-reports'
    def scanLevel = config.scanLevel ?: 'WARN'
    def scanTimeout = config.timeout ?: 30
    
    echo "🔒 Running OWASP ZAP Baseline Security Scan..."
    echo "   Target URL: ${targetUrl}"
    echo "   ZAP Image: ${zapImage}"
    echo "   Report Directory: ${reportDir}"
    echo "   Scan Level: ${scanLevel}"
    echo "   Timeout: ${scanTimeout} minutes"
    
    container('docker') {
        try {
            // Create report directory in workspace
            sh """
                mkdir -p ${reportDir}
                echo "📁 Created report directory: ${reportDir}"
            """
            
            // Run ZAP baseline scan in Docker container
            // -t: Target URL
            // -l: Alert level threshold (WARN = don't fail on findings)
            // -r: HTML report filename
            // -w: Markdown report filename  
            // -J: JSON report filename
            // --hook: Optional hook script for custom rules (not used here)
            def zapCommand = """
                docker run --rm \
                    -v \$(pwd)/${reportDir}:/zap/wrk:rw \
                    ${zapImage} \
                    zap-baseline.py \
                    -t ${targetUrl} \
                    -l ${scanLevel} \
                    -r report.html \
                    -w report.md \
                    -J report.json
            """
            
            timeout(time: scanTimeout, unit: 'MINUTES') {
                echo "🚀 Starting ZAP baseline scan (timeout: ${scanTimeout}min)..."
                echo "Command: ${zapCommand}"
                
                def exitCode = sh(
                    script: zapCommand,
                    returnStatus: true
                )
                
                // ZAP exit codes:
                // 0 = Success (no issues found)
                // 1 = Warning (issues found but below threshold)
                // 2 = Error (critical issues found)
                // 3+ = Scan failure
                
                if (exitCode == 0) {
                    echo "✅ ZAP scan completed successfully - No security issues found"
                } else if (exitCode == 1) {
                    echo "⚠️ ZAP scan completed - Security warnings found (not failing build due to -l ${scanLevel})"
                } else if (exitCode == 2) {
                    echo "⚠️ ZAP scan found critical issues (not failing build due to -l ${scanLevel})"
                } else {
                    error("❌ ZAP scan failed with exit code ${exitCode}")
                }
            }
            
            // Verify reports were generated
            sh """
                echo "📊 Verifying generated reports..."
                ls -lh ${reportDir}/
                
                if [ ! -f "${reportDir}/report.html" ]; then
                    echo "❌ Error: HTML report not found!"
                    exit 1
                fi
                
                if [ ! -f "${reportDir}/report.md" ]; then
                    echo "❌ Error: Markdown report not found!"
                    exit 1
                fi
                
                if [ ! -f "${reportDir}/report.json" ]; then
                    echo "❌ Error: JSON report not found!"
                    exit 1
                fi
                
                echo "✅ All reports generated successfully"
            """
            
            echo "🎉 OWASP ZAP scan completed successfully!"
            
        } catch (Exception e) {
            echo "❌ OWASP ZAP scan failed: ${e.message}"
            throw e
        } finally {
        // Always archive and publish reports, even if scan fails
        echo "📦 Archiving and publishing ZAP reports..."
        
        try {
            // Archive all report formats as Jenkins artifacts
            archiveArtifacts(
                artifacts: "${reportDir}/*",
                allowEmptyArchive: true,
                fingerprint: true
            )
            echo "✅ Reports archived successfully"
        } catch (Exception e) {
            echo "⚠️ Warning: Failed to archive artifacts: ${e.message}"
        }
        
            try {
                // Publish HTML report for easy viewing in Jenkins UI
                publishHTML([
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: reportDir,
                    reportFiles: 'report.html',
                    reportName: 'ZAP Baseline Report',
                    reportTitles: 'OWASP ZAP Security Scan'
                ])
                echo "✅ HTML report published successfully"
            } catch (Exception e) {
                echo "⚠️ Warning: Failed to publish HTML report: ${e.message}"
                echo "   Make sure 'HTML Publisher' plugin is installed in Jenkins"
            }
        }
    }
}
