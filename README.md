# Build Token Trigger Plugin

This plugin provides a pipeline step to trigger a build using the
  [Build Authorization Token Root](https://plugins.jenkins.io/build-token-root) plugin.

## Pipeline usage

Install Build Authorization Token Root on the target Jenkins controller and
configure an authentication token on the target job. On the controller running
the Pipeline, create a **Jenkins Build Token** credential for the target Jenkins
URL. Keep the token in Jenkins credentials rather than writing it in the
Jenkinsfile.

```groovy
pipeline {
    agent any

    stages {
        stage('Trigger remote build') {
            steps {
                buildTokenTrigger(
                    jenkinsUrl: 'https://jenkins.example.com',
                    job: 'folder/job',
                    credentialsId: 'remote-build-token',
                    parameters: [BRANCH: 'main']
                )
            }
        }
    }
}
```

Use `parameters: [:]` when the target build has no parameters. The step returns
the remote queue-item URL after the target Jenkins accepts the request; it does
not wait for the remote build to finish.

The direction of the request matters. An external webhook that calls
`/buildByToken/build` or `/buildByToken/buildWithParameters` is an inbound
trigger and does not need this Pipeline step. `buildTokenTrigger` is for a
running Pipeline that needs to initiate a build on a Jenkins controller.

See the [Pipeline step reference](https://www.jenkins.io/doc/pipeline/steps/build-token-trigger/)
for all supported options and the
[plugin page](https://plugins.jenkins.io/build-token-trigger/) for installation
and release information.
