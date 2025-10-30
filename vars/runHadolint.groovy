#!/usr/bin/env groovy

def call(Map config) {
    // Expects a list of Dockerfile paths, e.g., ['user-service/Dockerfile', 'todo-service/Dockerfile.test']
    def dockerfiles = config.dockerfiles
    def ignoreRules = config.ignoreRules ?: [] // Make ignoreRules an optional parameter, default to empty list

    // Construct the ignore flags string from the list of rules
    def ignoreFlags = ignoreRules.collect { rule -> "--ignore ${rule}" }.join(' ')

    container('docker') {
        echo "🧹 Running Hadolint for Dockerfiles in parallel..."
        
        def parallelLinting = [:]
        
        dockerfiles.each { dockerfilePath ->
            parallelLinting["Hadolint ${dockerfilePath}"] = {
                try {
                    echo "Linting ${dockerfilePath}..."
                    // Run Hadolint inside its official Docker container, mounting the workspace
                    // and passing the path to the Dockerfile to be linted.
                    // Use single quotes (' ' ') to prevent Groovy from interpolating ${env.WORKSPACE}
                    sh '''
                        docker run --rm \\
                            -v ${WORKSPACE}:/workspace --workdir /workspace \\
                            hadolint/hadolint \\
                            hadolint ''' + ignoreFlags + ' ' + dockerfilePath + '''
                    '''
                    echo "✅ Hadolint passed for ${dockerfilePath}!"
                } catch (e) {
                    echo "❌ Hadolint failed for ${dockerfilePath}!"
                    throw e
                }
            }
        }
        
        parallel parallelLinting
        
        echo "🎉 All Dockerfile linting completed!"
    }
}
