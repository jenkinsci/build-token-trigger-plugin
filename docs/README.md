# Build Token Trigger Plugin documentation

## Using the plugin

The Jenkins that you want to trigger a build on must have the [Build Authorization Token Root](https://plugins.jenkins.io/build-token-root) plugin installed.

You need to create a *Jenkins Build Token* credentials that contains the token.

The pipeline step then looks something like:

```
buildTokenTrigger jenkinsUrl: 'http://url.of.remote.jenkins', credentialsId: 'id-of-build-token-credentials', job: 'full/name/of/job', delay: 0, parameters: [a:'some value',b:'another value']
```
