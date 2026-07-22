## Keycloak

### Keycloak instance

Keycloak is deployed using the [Keycloak Operator](https://www.keycloak.org/guides#operator) and the [`Keycloak` resource](https://www.keycloak.org/operator/advanced-configuration).

#### Keycloak deployment specifics

> [!IMPORTANT]
> The Keycloak Operator works differently from most of the other Kubernetes operators: it only
> watches and manages the resources deployed in its own namespace, instead of watching resources in
> all the namespaces and deploying them in the same namespace as the original custom resource.

This Helm Chart has to do additional to support multiple namespaces:
1. the namespace in which the load test is created
2. the `keycloak-operator` namespace, in which the Keycloak Operator is deployed, and manages Keycloak resources.

As such, the resources it creates are a bit different from the rest of the resources.

> [!NOTE]
> The Keycloak Operator is evolving towards a cluster-wide model and a future
> upgrade may render the mitigations explained below not necessary anymore.
>
> Once the Keycloak Operator has an official release for cluster-wide
> operations, we can consider removing the duplication of all these resources.

##### Duplicated Secrets

> [!IMPORTANT]
> Caveats #1: Kubernetes Secrets used by Keycloak (PostgreSQL credentials and Keycloak admin user)
> are duplicated between the load test namespace and the `keycloak-operator` namespaces.

Keycloak requires:
1. PostgreSQL credentials, which are also used by the CNPG Operator (see below)
to create the Keycloak PostgreSQL user inside the database.
2. Its own "admin" credentials, which are also used by Identity to provision Keycloak.

Each of these Secrets are duplicated into the oad test namespace and the `keycloak-operator`
namespaces.

##### Naming Collisions

> [!IMPORTANT]
> Caveats #2: To prevent name collision between different load tests, resources deployed in the
> `keycloak-operator` namespace are prefixed by the name of the load test.

Also, since the `keycloak-operator` namespace may contain many Keycloak instances (one per load
test instance), the name of the resources deployed in that namespace **must** be different from each
other.

To support this, this Helm Chart prefixes the name of the load test to all the resources deployed
into the `keycloak-operator` namespace.

As such, the duplicated resources mentioned previously don't have the exact same name between the
resource in the load test namespace, and the resource in the `keycloak-operator` namespace.

### PostgreSQL cluster

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
