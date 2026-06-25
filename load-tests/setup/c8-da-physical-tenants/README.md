# Physical Tenants Load Test — `c8-da-physical-tenants`

Load test that exercises **two physical tenants** (`default` and `testfoo`) on a single Camunda 8.10
cluster using a shared PostgreSQL RDBMS with isolated table prefixes per tenant.

## Purpose

Validates that:
- Physical tenants are correctly isolated in the RDBMS (separate table sets per tenant).
- Load can be driven independently against each physical tenant via the REST routing path.
- The `DEFAULT_*` and `TESTFOO_*` table prefixes receive writes from their respective load testers.

## Architecture

```
GKE namespace: c8-da-physical-tenants
┌─────────────────────────────────────────────────────────┐
│  Camunda (3 brokers)                                    │
│   ├── physical tenant: default  → table prefix DEFAULT_ │
│   └── physical tenant: testfoo → table prefix TESTFOO_  │
│                                                         │
│  PostgreSQL (shared)                                    │
│   ├── DEFAULT_process_instance, DEFAULT_job, …         │
│   └── TESTFOO_process_instance, TESTFOO_job, …         │
│                                                         │
│  Load testers (typical scenario, 25 PI/s each)          │
│   ├── starter + 3 workers  → default  (gRPC)            │
│   └── starter-testfoo + 3 worker-testfoo → testfoo (REST)│
└─────────────────────────────────────────────────────────┘
```

**Key design note:** gRPC routing only reaches the default physical tenant. The testfoo load
testers must use REST (`preferRest=true`) so that requests are routed through
`http://camunda:8080/physical-tenants/testfoo`, which is the physical tenant REST endpoint.

## Files

| File | Purpose |
|------|---------|
| `Makefile` | Orchestrates platform and default load-test deployment |
| `camunda-platform-values-rdbms.yaml` | Physical tenant config: table prefixes, Keycloak OIDC assignment, testfoo authorizations |
| `camunda-platform-values-defaults.yaml` | Common platform defaults (node selectors, resources) |
| `camunda-platform-values-postgresql.yaml` | PostgreSQL connection settings |
| `load-test-values.yaml` | Default tenant load tester: credentials secret, stream-disabled, logging |
| `load-test-values-testfoo.yaml` | Overrides credentials secret to `load-test-credentials-testfoo` |
| `testfoo-load-test-manifests.yaml` | Pre-rendered Kubernetes manifests for testfoo starter + workers (REST-based) |
| `load-test-setup-values.yaml` | Namespace TTL, labels, load-test-setup chart config |

`testfoo-load-test-manifests.yaml` is used instead of a second Helm release because the
`camunda-load-tests` chart has hardcoded resource names (`starter`, `worker`) that prevent two
releases in the same namespace.

## Prerequisites

- `kubectl` configured for the target GKE cluster
- Helm v4.2.2 (the `camunda-platform-8.10` chart requires Helm ≥ v4):

  ```bash
  curl -fsSL https://get.helm.sh/helm-v4.2.2-linux-amd64.tar.gz | tar -xzf - -C /tmp/
  cp /tmp/linux-amd64/helm $HOME/bin/helm
  export PATH=$HOME/bin:$PATH
  ```

- The camunda-platform-helm chart cloned locally (gitignored, must be set up once per checkout):

  ```bash
  git clone https://github.com/camunda/camunda-platform-helm.git charts/camunda-platform-helm
  git -C charts/camunda-platform-helm checkout 347642d30179479f8ab8a2f00b2d979be05f5a8c
  ```

- The load-tests Helm chart:

  ```bash
  git clone https://github.com/camunda/camunda-load-tests-helm.git camunda-load-tests
  ```

## Deploy

### 1. Install load-test-setup (namespace, credentials, TTL label)

`install-load-test-setup` is separated from `make install` because the namespace must be adopted
by Helm before the remaining targets run:

```bash
export PATH=$HOME/bin:$PATH
helm upgrade load-test-setup charts/load-test-setup \
  --install \
  --namespace c8-da-physical-tenants \
  --reset-then-reuse-values \
  --create-namespace \
  --take-ownership \
  -f load-test-setup-values.yaml
```

### 2. Deploy platform and default load tester

```bash
make install-storage install-platform install-load-test scenario=typical
```

This installs PostgreSQL, the Camunda platform with physical tenant configuration, and the default
tenant load tester (25 PI/s, 3 workers).

### 3. Create the testfoo credentials secret

The testfoo load tester uses the same OAuth credentials as the default tenant but routes REST calls
to `/physical-tenants/testfoo`. Copy the `clientSecret` from the generated `load-test-credentials`
secret and create a parallel secret:

```bash
CLIENT_SECRET=$(kubectl get secret load-test-credentials \
  -n c8-da-physical-tenants \
  -o jsonpath='{.data.clientSecret}' | base64 -d)

kubectl create secret generic load-test-credentials-testfoo \
  --namespace c8-da-physical-tenants \
  --from-literal=clientId=orchestration \
  --from-literal=clientSecret="$CLIENT_SECRET" \
  --from-literal=zeebeRestAddress='http://camunda:8080/physical-tenants/testfoo' \
  --from-literal=zeebeGrpcAddress='http://camunda:26500' \
  --from-literal=authServer='http://keycloak/auth/realms/camunda-platform/protocol/openid-connect/token' \
  --from-literal=authType='OAUTH' \
  --from-literal=authorizationAudience='orchestration-api'
```

### 4. Deploy the testfoo load tester

```bash
kubectl apply -n c8-da-physical-tenants -f testfoo-load-test-manifests.yaml
```

## Verify

Confirm that both tenants are receiving writes by querying the RDBMS directly:

```bash
kubectl exec -n c8-da-physical-tenants postgresql-0 -- \
  env PGPASSWORD=camunda psql -U camunda -d camunda -c "
SELECT 'default' AS tenant, COUNT(*) AS process_instances FROM default_process_instance
UNION ALL
SELECT 'testfoo' AS tenant, COUNT(*) AS process_instances FROM testfoo_process_instance;"
```

Expected output (counts grow over time):

```
 tenant  | process_instances
---------+-------------------
 default |              7905
 testfoo |              2623
```

## Redeploy after `make clean`

After a clean, the namespace is deleted. Repeat steps 1–4 above. The
`load-test-credentials` secret is recreated by `install-load-test-setup`; the
`load-test-credentials-testfoo` secret must be created manually (step 3).
