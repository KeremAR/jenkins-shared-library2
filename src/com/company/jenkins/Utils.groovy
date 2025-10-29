package com.company.jenkins

class Utils implements Serializable {
    
    static def getPodTemplate() {
        return '''
            apiVersion: v1
            kind: Pod
            metadata:
              labels:
                jenkins: slave
            spec:
              serviceAccountName: jenkins
              imagePullSecrets:
                - name: ghcr-creds
              dnsConfig:
                options:
                  - name: ndots
                    value: "1"
              volumes:
              - name: docker-cache
                persistentVolumeClaim:
                  claimName: jenkins-docker-cache-pvc
              - name: trivy-cache
                persistentVolumeClaim:
                  claimName: jenkins-trivy-cache-pvc
              - name: tool-cache
                persistentVolumeClaim:
                  claimName: jenkins-tool-cache-pvc
              containers:
              - name: jnlp
                image: jenkins/inbound-agent:latest
                args: ['$(JENKINS_SECRET)', '$(JENKINS_NAME)']
                volumeMounts:
                - name: docker-cache
                  mountPath: /home/jenkins/.docker
                - name: trivy-cache
                  mountPath: /home/jenkins/.trivy-cache
                - name: tool-cache
                  mountPath: /home/jenkins/agent/tools
              - name: docker
                image: docker:20.10.16-dind
                securityContext:
                  privileged: true                
                volumeMounts:
                - name: docker-cache
                  mountPath: /var/lib/docker
                - name: trivy-cache
                  mountPath: /home/jenkins/.trivy-cache
              - name: argo
                image: "ghcr.io/keremar/jenkins-argo-agent:latest"
                command: ["sleep"]
                args: ["infinity"]
                tty: true
                resources:
                  requests:
                    memory: "128Mi"
                    cpu: "100m"
                  limits:
                    memory: "512Mi"
                    cpu: "500m"
   
        '''
    }
    
    static def notifyGitHub(steps, status, message, deploymentUrl = '') {
        steps.echo "${status == 'success' ? '✅' : '❌'} ${message}"
        if (status == 'success' && deploymentUrl) {
            steps.echo "🚀 Application deployed to: ${deploymentUrl}"
        }
    }
}