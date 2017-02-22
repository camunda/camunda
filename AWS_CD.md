# Continuous Delivery Overview

## Jenkins jobs
The jobs for automatically creating and provisioning the AWS infrastructure can be found in the [Optimize Jenkins](https://hq2.camunda.com/jenkins/optimize/view/AWS/).

They are created using [JobDSL](https://github.com/camunda/camunda-optimize/blob/master/.ci/jobs/aws_deployment.dsl).  

The jobs are executed in a [Docker image](https://github.com/camunda-ci/camunda-docker-ci-opstools)
```registry.camunda.com/camunda-ci-opstools```  
which contains the [used tools](#tools).

## Tools

For creating / provisioning in AWS, following tools are used:
- [Terraform](https://www.terraform.io/intro/index.html) for creating the components of the AWS infrastructure.
- [Ansible](https://docs.ansible.com/ansible/intro.html) for provisioning the components like DB / server in AWS.
- [AWS CLI](https://aws.amazon.com/cli/) for retrieving / modifying AWS settings.
