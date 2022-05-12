# Optimize Persistent Deployment

## Intro

The Optimize persistent deployment contains:
* An Optimize deployment (Optimize container + Cambpm container).
* An Elasticsearch cluster with persistent volume and data.
* A PostgreSQL database.

This environment is deployed under the `optimize-persistent` namespace of the `ci` Kubernetes cluster (the cluster
itself is maintained by the Infrastructure team).

The environment is split between 2 places:
* **[Infra-core](https://github.com/camunda/infra-core/blob/stage/camunda-ci/terraform/google/prod/db.tf#L145) repo**: Where you can find the definition of PostgreSQL 
  instance and its access.
* **[Optimize on-premise Helm Chart](https://github.com/camunda/camunda-optimize/tree/master/.ci/deployments-resources/helm-charts/optimize-onpremise)**:
  Where the Optimize application and ElasticSearch cluster are defined.
  
## Optimize On-premise Helm Chart 

### Intro

The [Optimize on-premise Helm Chart](https://github.com/camunda/camunda-optimize/tree/master/.ci/deployments-resources/helm-charts/optimize-onpremise) is created 
and configured to deploy Optimize Stage and Persistent environments. It is part of the Optimize project and owned by the 
Optimize team. It defines the deployment of the Optimize application and the Elasticsearch Cluster.

### Upgrade

You can deploy a new version of the Optimize application or Elasticsearch Cluster by running
the [deploy-optimize-persistent](../../../jobs/deploy_optimize_persistent.dsl) Job and pass the new version as 
parameter to the Job. Check the Job in [Jenkins](https://ci.optimize.camunda.cloud/job/deploy-optimize-persistent/). 

> This Job should run only on the prod Jenkins instance as we have only one Postgres instance and it is part of the 
> production environment.

## Elasticsearch Backup

ES backup (or snapshot as it is known for ES users) is done manually using the `curl` as following:

* Port-forward the ES http service to attach it to your localhost:
```shell script
kubectl -n optimize-persistent port-forward svc/elasticsearch-es-http 9200:9200
```

* Create a backup using `curl`:

```shell script
curl -X PUT "http://localhost:9200/_snapshot/optimize-persistent-data/<my-snapshot-name>"
```

You can see the created snapshot by checking the content of the [optimize-persistent-data](https://console.cloud.google.com/storage/browser/optimize-persistent-data;tab=objects?forceOnBucketsSortingFiltering=false&organizationId=669107107215&project=ci-30-162810&prefix=&forceOnObjectsSortingFiltering=false)
 bucket in the `camunda-ci` gcp project.

## PostgreSQL Database

### Database

The `optimize-persistent` database is part of the `camunda-ci` gcp project. You can see it [here](https://console.cloud.google.com/sql/instances/optimize-persistent/overview?organizationId=669107107215&project=ci-30-162810).

In order to access the database from the command line, you can follow the documentation [here](https://confluence.camunda.com/display/SRE/Connect+to+gcloud+SQL+database+instance).
The username and password used to access the database are stored in [vault](https://vault.int.camunda.com/ui/vault/secrets/secret/show/k8s-camunda-ci/optimize/db).

 > You can request access to the DB credentials from the Infra team. 

### Upgrade

In order to upgrade the `optimize-persistent` database, you can open a PR in github `infra-core` project and change [this line](https://github.com/camunda/infra-core/blob/stage/camunda-ci/terraform/google/prod/db.tf#L157).

### Backup

An automatic backup is configured for `optimize-persistent` nightly at 2am Berlin time.

