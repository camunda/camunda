## Persistent Deployment

### Intro 

This environment is an Optimize persistent deployment that contains:
* An Elasticsearch cluster with persistent volume and data.
* A postgres database.
* An Optimize deployment (Optimize container + Cambpm container).

This environment is deployed under the `optimize-persistent` namespace of the `ci-v2` Kubernetes cluster (the cluster 
itself is maintained by the Infrastructure team).

#### Elasticsearch Cluster

Before performing any change to the resources, you need to make sure you are in the right cluster and k8s context. 
See [this page](https://confluence.camunda.com/pages/viewpage.action?pageId=70785400) for more details.

##### Upgrade

The upgrade of the ES instance is done manually by changing the version in `.ci/persistent-deployment/elasticsearch/elasticsearch-cluster.yml`
file and run:

```shell script
kubectl apply -f .ci/persistent-deployment/elasticsearch/elasticsearch-cluster.yml 
```

##### Backup

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
 bucket in `camunda-ci-v2` gcp cluster.

#### Postgres database

##### Database

The `optimize-persistent` database is part of `camunda-ci-v2` gcp project. You can see it [here](https://console.cloud.google.com/sql/instances/optimize-persistent/overview?organizationId=669107107215&project=ci-30-162810).

In order to access the database from the command line, you can follow the documentation [here](https://confluence.camunda.com/display/SRE/Connect+to+gcloud+SQL+database+instance).
The username and password used to access the database are stored in [vault](https://vault.int.camunda.com/ui/vault/secrets/secret/show/k8s-camunda-ci-v2/optimize/db)
but this could only be access by INFRA team, so you should ask INFRA to share the credentials with you.

##### Upgrade

In order to upgrade the `optimize-persistent`, you can open a PR in github `infra-core` project and change [this line](https://github.com/camunda/infra-core/blob/stage/camunda-ci-v2/terraform/google/prod/db.tf#L69).

##### Backup

An automatic backup is configured for `optimize-persistent` nightly at 2am Berlin time.

