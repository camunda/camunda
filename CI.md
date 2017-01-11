# Continuous Integration for Camunda Optimize

Camunda Optimize uses Jenkins for Continuous Integration.

## JobDSL
Jobs are created using the JobDSL Plugin of Jenkins. It uses a Groovy DSL.
The jobs are stored inside the `.ci` folder of the `camunda-optimize` GitHub repository. The repository is monitored by the Jenkins and if there is a change, Jenkins will execute the so-called `seed-job`. The `seed-job` will then create/update/delete jobs accordingly to the dsl.

## Job DSL Api Viewer
[Job DSL Api Viewer inside Jenkins](https://hq2.camunda.com/jenkins/optimize/plugin/job-dsl/api-viewer/index.html)
