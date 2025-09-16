#!/usr/bin/env groovy

/**
 * Deploys a Helm chart to Kubernetes.
 *
 * @param config A map containing the configuration for the Helm deployment.
 *               - releaseName (required): The name for the Helm release.
 *               - chartPath (required): The path to the Helm chart directory.
 *               - namespace (required): The Kubernetes namespace to deploy into.
 *               - valuesFile (optional): The path to a custom values.yaml file.
 *               - imageTag (optional): The image tag to set in the Helm chart.
 *               - dockerConfigJsonCredentialsId (optional): The ID of the Jenkins 'Secret text' credential containing the base64 docker config.
 */
def call(Map config) {
    // --- Configuration Validation ---
    if (!config.releaseName || !config.chartPath || !config.namespace) {
        error("Missing required parameters: 'releaseName', 'chartPath', and 'namespace' must be provided.")
    }

    def releaseName = config.releaseName
    def chartPath = config.chartPath
    def namespace = config.namespace
    def valuesFile = config.valuesFile
    def imageTag = config.imageTag
    def dockerConfigJsonCredentialsId = config.dockerConfigJsonCredentialsId

    withCredentials([string(credentialsId: dockerConfigJsonCredentialsId, variable: 'DOCKER_CONFIG_JSON_B64')]) {
        container('docker') {
            echo "🔧 Installing Helm..."
            sh '''
                apk add --no-cache curl bash
                curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
                chmod 700 get_helm.sh
                ./get_helm.sh
            '''

            echo "--- DEBUGGING (PRE-FLIGHT CHECK) ---"
            echo "Step 1: Verifying chart file structure inside the container"
            sh "ls -lR helm-charts/todo-app"

            echo "Step 2: Linting the Helm chart to check for errors"
            sh "helm lint ${chartPath}"

            echo "Step 3: Checking if the credential variable was loaded."
            sh 'echo "Credential character count: ${#DOCKER_CONFIG_JSON_B64}"'

            echo "Step 4: Rendering the full Helm template to inspect the Secret YAML."
            def helmTemplateCmd = "helm template ${releaseName} ${chartPath} --namespace ${namespace}"
            if (valuesFile) {
                helmTemplateCmd += " -f ${valuesFile}"
            }
            // Use single quotes for the groovy string, so the shell can expand the variable
            // Pipe the output to grep to find our secret, and then to cat to print it.
            // Bu komut, 'global.imagePullSecrets.dockerconfigjson' değerinin template'e doğru şekilde geçip geçmediğini doğrulamak için kullanılır.
            sh helmTemplateCmd + ' --set global.imagePullSecrets.dockerconfigjson="$DOCKER_CONFIG_JSON_B64" | grep -A 5 "kind: Secret" || echo "Secret template did not render."'
            echo "--- END DEBUGGING ---"

            echo "🚀 Deploying with Helm..."
            
            // Helm upgrade komutu, belirtilen sürüm yoksa onu kurar (install), varsa günceller (upgrade).
            // --create-namespace: Eğer namespace mevcut değilse oluşturur.
            // --wait: Dağıtımın tamamlanmasını ve tüm pod'ların hazır olmasını bekler.
            // --timeout: 'wait' işleminin ne kadar süre bekleyeceğini belirtir.
            def helmCmd = "helm upgrade --install ${releaseName} ${chartPath} --namespace ${namespace} --create-namespace --wait --timeout=5m"

            // Eğer bir values dosyası belirtilmişse, komuta eklenir.
            // Bu, staging ve production için farklı konfigürasyonlar kullanmamızı sağlar.
            if (valuesFile) {
                helmCmd += " -f ${valuesFile}"
            }

            // Eğer bir image tag'i belirtilmişse, bu Helm chart'ındaki 'global.image.tag' değerini ezer (override).
            // Bu, her pipeline çalıştığında tüm servisler için yeni oluşturulan imajı dağıtmamızı sağlar.
            if (imageTag) {
                helmCmd += " --set global.image.tag=${imageTag}"
            }
            
            echo "Executing Helm command..." // Güvenlik nedeniyle tam komutu loglara yazdırmıyoruz.
            
            if (dockerConfigJsonCredentialsId) {
                // Özel Docker registry'sinden imaj çekebilmek için 'dockerconfigjson' credential'ını Helm komutuna iletiyoruz.
                // Groovy'nin string'i içinde shell değişkeninin ($DOCKER_CONFIG_JSON_B64) doğru şekilde okunabilmesi için tırnak işaretlerine dikkat edilmelidir.
                sh helmCmd + ' --set global.imagePullSecrets.dockerconfigjson="$DOCKER_CONFIG_JSON_B64"'
            } else {
                sh helmCmd
            }
            
            echo "✅ Helm deployment for release '${releaseName}' completed successfully!"
        }
    }
}
