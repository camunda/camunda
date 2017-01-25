# Continuous Integration for Camunda Optimize

Camunda Optimize uses Jenkins for Continuous Integration.

## Pipeline and JobDSL
Jobs are created using the Jenkins Pipeline plugin and also the JobDSL Plugin. It uses a Groovy DSL.
Jenkins Pipeline uses a file in the root of the repository called `Jenkinsfile`. It is written in Groovy following the Pipeline syntax.

# Views

The JobDSL jobs and views are stored inside the `.ci` folder of the `camunda-optimize` GitHub repository. 
The repository is monitored by the Jenkins and if there is a change, Jenkins will execute the so-called `seed-job`. 
The `seed-job` will then create/update/delete jobs accordingly to the dsl.

# Useful resources for writing jobs

[Job DSL Api Viewer inside Jenkins](https://hq2.camunda.com/jenkins/optimize/plugin/job-dsl/api-viewer/index.html)
[Jenkinsfile](https://github.com/camunda/camunda-optimize/blob/master/Jenkinsfile)
[Pipeline syntax](https://hq2.camunda.com/jenkins/optimize/view/All/job/camunda-optimize/pipeline-syntax/)
