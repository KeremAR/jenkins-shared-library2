#!/usr/bin/env groovy

def call(Map config) {
    def serviceName = config.serviceName
    def dockerfilePath = config.dockerfilePath ?: "${serviceName}/Dockerfile"
    def contextPath = config.contextPath ?: "."
    def registry = config.registry ?: 'ghcr.io'
    def username = config.username ?: 'keremar'
    def imageTag = config.imageTag ?: env.BUILD_NUMBER
    
    def imageName = "${registry}/${username}/todo-app-${serviceName}:${imageTag}"
    def latestImageName = "${registry}/${username}/todo-app-${serviceName}:latest"
    
    container('docker') {
        script {
            echo "🔨 Building ${serviceName} Docker image..."
            sh "docker build -t ${imageName} -f ${dockerfilePath} ${contextPath}"
            sh "docker tag ${imageName} ${latestImageName}"
            echo "✅ Successfully built ${serviceName} image: ${imageName}"
            
            // Return image names for later use
            return [
                versioned: imageName,
                latest: latestImageName
            ]
        }
    }
}