#!/usr/bin/env groovy

def call(Map config) {
    def images = config.images // List of image names to push
    def registry = config.registry ?: 'ghcr.io'
    def credentialsId = config.credentialsId ?: 'github-registry'
    
    container('docker') {
        withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'REGISTRY_PASSWORD', usernameVariable: 'REGISTRY_USERNAME')]) {
            echo "🚀 Pushing images to ${registry}..."
            
            sh "echo \$REGISTRY_PASSWORD | docker login ${registry} -u \$REGISTRY_USERNAME --password-stdin"
            
            images.each { image ->
                echo "📦 Pushing ${image}..."
                sh "docker push ${image}"
                echo "✅ Successfully pushed: ${image}"
            }
            
            echo "🎉 All images pushed successfully!"
        }
    }
}