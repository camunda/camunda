## Keycloak

### PostgreSQL

Keycloak is backed by PostgreSQL (PG). The PG cluster is deployed using the [CloudNativePG Operator (CNPG)](https://cloudnative-pg.io/), which manages:

* The PG cluster itself, via a [`Cluster` resource](https://cloudnative-pg.io/docs/1.30/cloudnative-pg.v1#cluster)
* The PG internal resources:
  * The PG role used by Keycloak to connect into the PG cluster, via a [`DatabaseRole` resource](https://cloudnative-pg.io/docs/1.30/declarative_role_management#the-databaserole-resource)
  * The PG database used to store Keycloak data, via a [`Database` resource](https://cloudnative-pg.io/docs/1.30/declarative_database_management)

When a CNPG cluster is created, the CNPG Operator creates the underlying Kubernetes resources (non-exhaustive list, see [the doc](https://cloudnative-pg.io/docs/1.30/) for the full details):

1. A dedicated Kubernetes service account: the SA exists in the target namespace and represent the Kubernetes identity used by the underlying PG cluster
2. The Kubernetes RBAC to allow the SA to read its secrets, etc.
3. New pod(s) to represent the actual PostgreSQL node(s)

The Operator then initialize the PG cluster based on its configuration and the additional CNPG resources defined in Kubernetes, in particular:

* The `DatabaseRole` resources creates PostgreSQL roles
* The `Database` resources creates the PostgreSQL databases

> [!NOTE]
> The reconciliation of the resources managed by the operator are all done
> asynchronously and it may take a couple of minutes for the PostgreSQL cluster
> to be fully configured and available to be used by Keycloak.
> During the initialization phase, Keycloak may fail a couple of times to
> connect to the not-yet-initialized cluster.

