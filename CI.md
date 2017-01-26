# CI/CD overview for Camunda Optimize

Camunda Optimize uses [Jenkins](https://jenkins.io/) for CI/CD.

## Pipeline and JobDSL
Jobs are created using the Jenkins Pipeline plugin and also the JobDSL Plugin. Both use a type of Groovy DSL but differ in terms of capability.
Jenkins Pipeline requires a file named [Jenkinsfile](https://github.com/camunda/camunda-optimize/blob/master/Jenkinsfile) in the root of the repository to be present.
When you create a branch and push it to GitHub and it contains a `Jenkinsfile`, the branch will be automatically detected by Jenkins and the pipeline jobs will be executed for it.  
To disable the CI for a branch, prefix the branch with `noci-`.

# Views

The JobDSL jobs and views are stored inside the `.ci` folder of the `camunda-optimize` GitHub repository.
The repository is monitored by the Jenkins and if there is a change, Jenkins will execute the so-called `seed-job`.
The `seed-job` will then create/update/delete jobs accordingly to the dsl.

# Docker images

The [Camunda Optimize Jenkins](https://hq2.camunda.com/jenkins/optimize/) uses the docker image
```
registry.camunda.com/camunda-ci-optimize-build:latest
```
for running the jobs. It can be found [here](https://github.com/camunda-ci/camunda-docker-ci-optimize-build).

# Useful resources for writing pipelines / jobs

[Job DSL Api Viewer inside Jenkins](https://hq2.camunda.com/jenkins/optimize/plugin/job-dsl/api-viewer/index.html)  
[Jenkinsfile](https://github.com/camunda/camunda-optimize/blob/master/Jenkinsfile)  
[Pipeline syntax](https://hq2.camunda.com/jenkins/optimize/view/All/job/camunda-optimize/pipeline-syntax/)  
[Declarative pipeline syntax](https://github.com/jenkinsci/pipeline-model-definition-plugin/wiki/Getting-Started)  
