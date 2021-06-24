# Optimize Persistent Deployment

## Intro

This environment is an Optimize persistent deployment that contains:
* An Optimize deployment (Optimize container + Cambpm container).
* An Elasticsearch cluster with persistent volume and data.
* A PostgreSQL database.

This environment is deployed under the `optimize-persistent` namespace of the `ci` Kubernetes cluster (the cluster
itself is maintained by the Infrastructure team).

The environment is split between 2 places:
* **Static resources**: The resources tend to change less frequently or need a special permission to run and
  those are managed in the [infra-core](https://github.com/camunda/infra-core/) repo.
  For example, PostgreSQL instance and its access is managed in the infra-core repo.
* **Dynamic resources**: The resources tend to change more frequently, and they are managed by Optimize team using
  Optimize Jenkins.

## 1. Optimize Application

### Upgrade

* The upgrade of the Optimize deployment, change the version in `optimize/deployment.yml`
* Now go to Jenkins instance of targeted infrastructure env (e.g. prod, stage, etc).
* Run the job [deploy-optimize-persistent](../jobs/deploy_optimize_persistent.dsl)
  then, `Build with Parameters` and tick `DEPLOY_OPTIMIZE` to deploy the new version of Optimize.

## 2. Elasticsearch Cluster

### Upgrade

* The upgrade of the ES instance, change the version in `elasticsearch/elasticsearch-cluster.yml`
* Now go to Jenkins instance of targeted infrastructure env (e.g. prod, stage, etc).
* Run the job [deploy-optimize-persistent](../jobs/deploy_optimize_persistent.dsl)
  then, `Build with Parameters` and tick `DEPLOY_ELASTICSEARCH` to deploy the new version of ES.

### Backup

ES backup (or snapshot as it is known for ES users) is done manually using the `curl` as following:

* Port-forward the ES http service to attach it to your localhost:
```shell script
kubectl -n optimize-persistent svc/elasticsearch-es-http 9200:9200
```

* Create a backup using `curl`:

```shell script
curl -X PUT "http://localhost:9200/_snapshot/optimize-persistent-data/<my-snapshot-name>"
```

You can see the created snapshot by checking the content of the [optimize-persistent-data](https://console.cloud.google.com/storage/browser/optimize-persistent-data;tab=objects?forceOnBucketsSortingFiltering=false&organizationId=669107107215&project=ci-30-162810&prefix=&forceOnObjectsSortingFiltering=false)
 bucket in the `camunda-ci` gcp project.

## 3. PostgreSQL Database

### Database

The `optimize-persistent` database is part of the `camunda-ci` gcp project. You can see it [here](https://console.cloud.google.com/sql/instances/optimize-persistent/overview?organizationId=669107107215&project=ci-30-162810).

In order to access the database from the command line, you can follow the documentation [here](https://confluence.camunda.com/display/SRE/Connect+to+gcloud+SQL+database+instance).
The username and password used to access the database are stored in [vault](https://vault.int.camunda.com/ui/vault/secrets/secret/show/k8s-camunda-ci/optimize/db)
but this could only be access by INFRA team, so you should ask INFRA to share the credentials with you.

### Upgrade

In order to upgrade the `optimize-persistent`, you can open a PR in github `infra-core` project and change [this line](https://github.com/camunda/infra-core/blob/stage/camunda-ci/terraform/google/prod/db.tf#L69).

### Backup

An automatic backup is configured for `optimize-persistent` nightly at 2am Berlin time.
