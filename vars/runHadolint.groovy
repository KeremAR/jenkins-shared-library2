#!/usr/bin/env groovy

def call(Map config) {
    // Expects a list of Dockerfile paths, e.g., ['user-service/Dockerfile', 'todo-service/Dockerfile.test']
    def dockerfiles = config.dockerfiles

    container('docker') {
        echo "🧹 Running Hadolint for Dockerfiles in parallel..."
        
        def parallelLinting = [:]
        
        dockerfiles.each { dockerfilePath ->
            parallelLinting["Lint ${dockerfilePath}"] = {
                try {
                    echo "Linting ${dockerfilePath}..."
                    // Run Hadolint inside its official Docker container, mounting the workspace
                    // and passing the path to the Dockerfile to be linted.
                    sh """
                        docker run --rm \\
                            -v \${env.WORKSPACE}:/workspace --workdir /workspace \\
                            hadolint/hadolint \\
                            hadolint ${dockerfilePath}
                    """
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
