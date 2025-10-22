#!/usr/bin/env groovy

def call(Map config) {
    def serviceName = config.serviceName
    def appName = config.appName
    def dockerfilePath = config.dockerfilePath ?: "${serviceName}/Dockerfile"
    def contextPath = config.contextPath ?: "."
    def registry = config.registry
    def username = config.username
    def imageTag = config.imageTag
    
    def imageName = "${registry}/${username}/${appName}-${serviceName}:${imageTag}"
    def latestImageName = "${registry}/${username}/${appName}-${serviceName}:latest"
    
    container('docker') {
        script {
            echo "🔨 Building ${serviceName} Docker image..."
            sh "docker build  -t ${imageName} -f ${dockerfilePath} ${contextPath}"
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