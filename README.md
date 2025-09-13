# Jenkins Shared Library for Todo App CI/CD

Bu shared library Todo App projesi için Jenkins pipeline fonksiyonlarını içerir.

## Yapı

- `vars/` - Global pipeline fonksiyonları
- `src/` - Yardımcı sınıflar ve utilities  
- `resources/` - Template dosyalar ve kaynaklar

## Kullanım

```groovy
@Library('todo-app-shared-library') _

pipeline {
    agent {
        kubernetes {
            yaml com.company.jenkins.Utils.getPodTemplate()
        }
    }
    
    stages {
        stage('Build & Test') {
            steps {
                script {
                    def images = buildAllServices(com.company.jenkins.Utils.getServiceConfig())
                    runBackendTests()
                    pushToRegistry(images: images)
                    deployToKubernetes(namespace: 'todo-app')
                }
            }
        }
    }
}
```

## Fonksiyonlar

### buildDockerImage(config)
Tek bir Docker service'i build eder.

### buildAllServices(config) 
Tüm servisleri paralel olarak build eder.

### pushToRegistry(config)
Image'ları container registry'ye push eder.

### runBackendTests(config)
Backend testlerini çalıştırır.

### deployToKubernetes(config)
Kubernetes'e deployment yapar.