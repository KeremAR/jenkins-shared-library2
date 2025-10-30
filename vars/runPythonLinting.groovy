#!/usr/bin/env groovy

def call(Map config) {
    // Expects a list of Python file paths or directories, e.g., ['user-service/', 'todo-service/']
    def pythonTargets = config.pythonTargets ?: ['user-service/', 'todo-service/']
    def flake8Args = config.flake8Args ?: '--max-line-length=88 --extend-ignore=E203'
    def blackVersion = config.blackVersion ?: '23.3.0'
    def flake8Version = config.flake8Version ?: '6.0.0'

    container('python') {
        echo "🧹 Running Black and Flake8 for Python code..."

        // Install specific versions of black and flake8
        sh "pip install black==${blackVersion} flake8==${flake8Version}"

        def parallelLinting = [:]

        pythonTargets.each { target ->
            parallelLinting["Black & Flake8 ${target}"] = {
                try {
                    echo "Formatting check with Black for ${target}..."
                    sh "black --check ${target}"
                    echo "✅ Black passed for ${target}!"
                } catch (e) {
                    echo "❌ Black failed for ${target}!"
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
