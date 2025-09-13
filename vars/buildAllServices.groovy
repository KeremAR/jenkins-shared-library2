#!/usr/bin/env groovy

def call(Map config) {
    def services = config.services ?: [
        [name: 'user-service', dockerfile: 'user-service/Dockerfile'],
        [name: 'todo-service', dockerfile: 'todo-service/Dockerfile'],
        [name: 'frontend', dockerfile: 'frontend2/frontend/Dockerfile', context: 'frontend2/frontend/']
    ]
    def registry = config.registry ?: 'ghcr.io'
    def username = config.username ?: 'keremar'
    def imageTag = config.imageTag ?: env.BUILD_NUMBER
    
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
                imageTag: imageTag
            ]
            
            def images = buildDockerImage(buildConfig)
            builtImages.addAll([images.versioned, images.latest])
        }
    }
    
    parallel parallelBuilds
    
    return builtImages
}