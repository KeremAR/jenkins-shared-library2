## Repository Overview

This is a Jenkins Shared Library for Todo App CI/CD pipelines, written in Groovy. The library provides reusable functions for building, testing, security scanning, and deploying containerized applications to Kubernetes using multiple deployment strategies (Helm, Kustomize, ArgoCD).

## Key Architecture Components

### Directory Structure
- `vars/` - Global pipeline functions (the main library functions)
- `src/com/company/jenkins/` - Utility classes (Utils.groovy with pod templates and notification helpers)
- `examples/` - Sample Jenkinsfile configurations

### Core Pipeline Functions

**Build & Test Functions:**
- `buildDockerImage()` - Builds individual Docker services with versioned and latest tags
- `buildAllServices()` - Orchestrates parallel builds of multiple services
- `runUnitTests()` - Runs Docker-based unit tests with multi-stage builds
- `runIntegrationTests()` - Integration testing with docker-compose
- `pushToRegistry()` - Pushes images to container registry with credentials

**Security & Quality Functions:**
- `runTrivyScan()` - Vulnerability scanning with configurable severity levels and caching
- `runHadolint()` - Dockerfile linting with customizable rule exclusions
- `sonarQubeAnalysis()` - Code quality analysis with Quality Gates

**Deployment Functions:**
- `deployWithHelm()` - Helm-based deployments with values overrides and secret injection
- `deployWithKustomize()` - Kustomize-based deployments
- `argoDeployStaging()` / `argoDeployProduction()` - GitOps deployments via ArgoCD CLI
- `ensureArgoCD()` - Downloads ArgoCD CLI if not present

**Environment-Specific Deployment:**
- `deployToStaging()` / `deployToProduction()` - Environment-aware deployment wrappers
- `cleanupHelmRelease()` / `cleanupKustomizeRelease()` - Resource cleanup functions

### Pipeline Flow Architecture

The library implements a multi-stage CI/CD flow:
1. **Validation Stage** (all branches except tags): Build → Test → Security Scan
2. **Integration Stage** (master branch): Push to Registry → Deploy to Staging
3. **Production Stage** (git tags v*): Cleanup Staging → Deploy to Production



### Working with Pipeline Functions

When testing pipeline functions, use the example Jenkinsfile:
```bash
# View the complete example configuration
Get-Content examples/Jenkinsfile-simple
```

The example shows how to configure:
- Service definitions with custom Dockerfiles and contexts
- Registry and deployment settings
- Security scanning parameters (Trivy, Hadolint)
- ArgoCD and Helm deployment configurations



### Deployment Strategies

The library supports multiple deployment approaches:

1. **Helm Deployments** - Uses `deployWithHelm()` with support for:
   - Custom values files
   - Image tag overrides
   - Docker registry secrets injection
   - Release management and rollbacks

2. **ArgoCD GitOps** - Uses `argoDeployStaging()` / `argoDeployProduction()` for:
   - Git tag-based production deployments
   - Automated application synchronization
   - Health checks and wait conditions

3. **Kustomize** - Alternative to Helm for simpler overlay-based deployments

## Configuration Patterns

### Service Configuration
```groovy
services: [
    [name: 'user-service', dockerfile: 'user-service/Dockerfile'],
    [name: 'frontend', dockerfile: 'frontend2/frontend/Dockerfile', context: 'frontend2/frontend/']
]
```

### Security Scanning Configuration
```groovy
// Hadolint (currently enabled)
dockerfilesToHadolint: ['user-service/Dockerfile', 'todo-service/Dockerfile'],
hadolintIgnoreRules: ['DL3008', 'DL3009', 'DL3016', 'DL3059'],

// Trivy (commented out in example but functional)
trivySeverities: 'HIGH,CRITICAL',
trivyFailBuild: true,
trivySkipDirs: ['/app/node_modules']
```

## Development Notes

- The library is designed for Kubernetes-based deployments with Jenkins running in pods
- All Docker operations run in privileged containers with Docker-in-Docker
- Turkish comments are preserved in the codebase (mixed language documentation)
- Many advanced features (SonarQube, Trivy, Integration tests) are implemented but disabled in the example
- The Utils class provides standardized pod templates and GitHub notification helpers
- Pipeline functions use container('docker') blocks for Docker operations