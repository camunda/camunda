## Keycloak

### PostgreSQL

Keycloak is backed by PostgreSQL (PG). The PG cluster is deployed using the [CloudNativePG Operator (CNPG)](https://cloudnative-pg.io/), which manages:

* The PG cluster itself, via a [`Cluster` resource](https://cloudnative-pg.io/docs/1.30/cloudnative-pg.v1#cluster)
* The Kubernetes Secret to share the username/password used by the Keycloak user to connect into PostgreSQL itself.

When a CNPG cluster is created, the CNPG Operator creates the underlying Kubernetes resources
(non-exhaustive list, see [the doc](https://cloudnative-pg.io/docs/1.30/) for the full details):

1. A dedicated Kubernetes service account: the SA exists in the target namespace and represent the
   Kubernetes identity used by the underlying PG cluster
2. The Kubernetes RBAC to allow the SA to read its secrets, etc.
3. New pod(s) to represent the actual PostgreSQL node(s)

The PG cluster is immediately initialized at creation time using the `bootstrap` mechanism, with a
single database owned by a single user. This ensures that when the PG cluster starts, it's already
usable by Keycloak without further provisioning to be done.
