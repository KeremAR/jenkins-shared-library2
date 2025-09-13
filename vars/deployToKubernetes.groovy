#!/usr/bin/env groovy

def call(Map config) {
    def namespace = config.namespace ?: 'todo-app'
    def manifestsPath = config.manifestsPath ?: 'k8s'
    def services = config.services ?: ['user-service', 'todo-service', 'frontend']
    
    container('docker') {
        echo "🔧 Installing kubectl..."
        sh '''
            apk add --no-cache curl
            curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl"
            chmod +x kubectl
            mv kubectl /usr/local/bin/
        '''
        
        echo "🚀 Deploying to Kubernetes..."
        
        // Test connectivity
        sh 'kubectl version --client'
        sh 'kubectl get nodes || echo "No direct cluster access - using service account"'
        
        // Apply manifests
        echo "📋 Applying Kubernetes manifests..."
        sh "kubectl apply -f ${manifestsPath}/namespace.yaml"
        
        services.each { service ->
            echo "Deploying ${service}..."
            sh "kubectl apply -f ${manifestsPath}/${service}-deployment.yaml"
            sh "kubectl apply -f ${manifestsPath}/${service}-service.yaml"
        }
        
        // Apply ingress
        sh "kubectl apply -f ${manifestsPath}/ingress.yaml"
        
        // Restart deployments to pull latest images
        echo "🔄 Restarting deployments..."
        services.each { service ->
            sh "kubectl rollout restart deployment/${service} -n ${namespace}"
        }
        
        // Wait for rollout
        echo "⏳ Waiting for deployments to complete..."
        services.each { service ->
            sh "kubectl rollout status deployment/${service} -n ${namespace} --timeout=300s"
        }
        
        echo "✅ Kubernetes deployment completed successfully!"
    }
}