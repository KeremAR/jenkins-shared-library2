#!/usr/bin/env groovy

def call(Map config) {
    // Expects a list of Python file paths or directories, e.g., ['user-service/', 'todo-service/']
    def pythonTargets = config.pythonTargets ?: ['user-service/', 'todo-service/']
    def flake8Args = config.flake8Args ?: '--max-line-length=88 --extend-ignore=E203'
    def blackVersion = config.blackVersion ?: '25.9.0'
    def flake8Version = config.flake8Version ?: '7.3.0'

    container('pythonlinting') {
        echo "🧹 Running Black and Flake8 for Python code in parallel (custom container)..."

        def parallelLinting = [:]

        pythonTargets.each { target ->
            parallelLinting["Lint ${target}"] = {
                try {
                    echo "Formatting check with Black for ${target}..."
                    sh "black --check --diff --color ${target}"
                    echo "✅ Black passed for ${target}!"
                } catch (e) {
                    echo "❌ Black failed for ${target}!"
                    echo "Black otomatik düzeltme için: black ${target} komutunu çalıştırabilirsiniz."
                    throw e
                }
                try {
                    echo "Linting with Flake8 for ${target}..."
                    sh "flake8 ${flake8Args} ${target}"
                    echo "✅ Flake8 passed for ${target}!"
                } catch (e) {
                    echo "❌ Flake8 failed for ${target}!"
                    throw e
                }
            }
        }

        parallel parallelLinting

        echo "🎉 All Python linting completed!"
    }
}
