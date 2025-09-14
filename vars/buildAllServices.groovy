#!/usr/bin/env groovy

def call(Map config) {
    def services = config.services
    def registry = config.registry
    def username = config.username
    def imageTag = config.imageTag
    def appName = config.appName
    
    def builtImages = []
    
    // Build all services in parallel
    def parallelBuilds = [:]
    
    services.each { service ->
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
    
    return builtImages
}