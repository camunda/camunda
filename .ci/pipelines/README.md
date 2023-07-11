# Optimize Pipelines 

## Intro 

This file will present the different ways of deploying the K8s resources used in Optimize pipelines.

## Using deploy scripts 

For some pipelines (e.g [import_static_data_performance.groovy](import_static_data_performance.groovy)), Optimize uses the 
`K8s resources` and `deploy` scripts defined in [podSpecs/performanceTests](../podSpecs/performanceTests) to deploy the K8s resources needed for the CI. 

## Using Jenkins K8s plugin

For other pipelines (e.g [query_performance.groovy](query_performance.groovy)), Optimize deploy the k8s resources using 
the k8s Jenkins plugin as you see below:

```bash
  agent {
    kubernetes {
      cloud 'optimize-ci'
      label "optimize-ci-build-${env.JOB_BASE_NAME.replaceAll("%2F", "-").replaceAll("\\.", "-").take(10)}-${env.BUILD_ID}"
      defaultContainer 'jnlp'
      yaml queryPerformanceConfig(env.ES_VERSION, env.CAMBPM_VERSION)
    }
  }
```
where `queryPerformanceConfig` function returns the Pod to use:

```bash
static String queryPerformanceConfig(esVersion, camBpmVersion) {
  return """
metadata:
  labels:
    agent: optimize-ci-build
spec:
  ...
}
```

