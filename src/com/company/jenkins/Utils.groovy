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
              volumes:
              - name: docker-cache
                persistentVolumeClaim:
                  claimName: jenkins-docker-cache-pvc
              containers:
              - name: jnlp
                image: jenkins/inbound-agent:latest
                args: ['$(JENKINS_SECRET)', '$(JENKINS_NAME)']
                volumeMounts:
                - name: docker-cache
                  mountPath: /home/jenkins/.docker
              - name: docker
                image: docker:20.10.16-dind
                securityContext:
                  privileged: true
                volumeMounts:
                - name: docker-cache
                  mountPath: /var/lib/docker
              - name: argocd
                image: argoproj/argocd:v3.1.5 
                command:
                - sleep
                args:
                - 99d
        '''
    }
    
    static def notifyGitHub(steps, status, message, deploymentUrl = '') {
        steps.echo "${status == 'success' ? '✅' : '❌'} ${message}"
        if (status == 'success' && deploymentUrl) {
            steps.echo "🚀 Application deployed to: ${deploymentUrl}"
        }
    }
}