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
        '''
    }
    
    static def getServiceConfig() {
        return [
            services: [
                [name: 'user-service', dockerfile: 'user-service/Dockerfile'],
                [name: 'todo-service', dockerfile: 'todo-service/Dockerfile'], 
                [name: 'frontend', dockerfile: 'frontend2/frontend/Dockerfile', context: 'frontend2/frontend/']
            ],
            registry: 'ghcr.io',
            username: 'keremar',
            namespace: 'todo-app'
        ]
    }
    
    static def notifyGitHub(steps, status, message) {
        steps.echo "${status == 'success' ? '✅' : '❌'} ${message}"
        if (status == 'success') {
            steps.echo "🚀 Application deployed to: http://todo-app.local"
        }
    }
}