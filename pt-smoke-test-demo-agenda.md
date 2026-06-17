# PT Identity — team demo agenda (local notes)

> Local prep notes, not part of the harness deliverable. Branch: `meg-pt-identity-smoke-testing-3600`.
> Rebased onto `main` (`e877432b160`) — smoke tests exercise current main's PT code
> ([`authentication/.../pt/`](authentication/src/main/java/io/camunda/authentication/pt), CSL `0.1.0-alpha31`).

## Goal

Show that per-physical-tenant identity isolation works end-to-end: how OC tells CSL what chains to
build, what one realistic setup looks like, and the isolation proven live.

---

## 1. The setup to show — Scenario D (3 tenants)

**Decision: lead with D as the headline run.** `default` + `tenanta` + `tenantb`, three Keycloak
realms, each PT assigned only its own provider → each rejects the others' tokens even though all are
valid cluster providers. PT-to-PT rejection is the clearest "real multi-tenancy" visual and the most
representative of the product.

The **config** and the **diagram** below are the same setup two ways. The config is the effective
Scenario D — the two-tenants overlay merged into the base for display (trimmed to the auth + tenant
structure; secrets, ES, logging, init omitted). The app still loads the split files:
[`application-pt-smoke-test.yaml`](dist/src/main/resources/application-pt-smoke-test.yaml)
+ [`application-pt-smoke-test-two-tenants.yaml`](dist/src/main/resources/application-pt-smoke-test-two-tenants.yaml).

```yaml
camunda:
  security:
    authentication:
      method: oidc
      oidc:                              # default slot → default realm :8081
        client-id: camunda-pt-default-client
        issuer-uri: http://localhost:8081/realms/default
        audiences: [pt-default-aud]
      providers:
        oidc:
          tenanta:                       # tenanta realm :8082 (default's view)
            client-id: camunda-pt-default-via-tenanta-client
            issuer-uri: http://localhost:8082/realms/tenanta
            audiences: [pt-default-via-tenanta-aud]
          tenantb:                       # tenantb realm :8083        ← Scenario D overlay
            client-id: camunda-pt-tenantb-client
            issuer-uri: http://localhost:8083/realms/tenantb
            audiences: [pt-tenantb-aud]
  physical-tenants:
    # no explicit `default` tenant config ⇒ the default PT is set up implicitly and carries the
    # full cluster set (oidc + tenanta + tenantb).
    tenanta:                             # assigned [tenanta] — overrides to its own client + audience
      security:
        authentication:
          providers:
            assigned: [tenanta]
            oidc:
              tenanta:
                client-id: camunda-pt-tenanta-client
                audiences: [pt-tenanta-aud]
    tenantb:                             # assigned [tenantb]         ← Scenario D overlay
      security:
        authentication:
          providers:
            assigned: [tenantb]
```

The same mapping, at a glance (IdP → PT, by audience):

```text
  3 IdP realms (Keycloak)            3 PTs (each ⇒ one audience-scoped SecurityFilterChain)
  ─────────────────────────────────────────────────────────────────────────────────────────

  PT default   — no `assigned` ⇒ inherits the FULL cluster provider set
     ◄── default :8081   aud pt-default-aud              (camunda-pt-default-client)
     ◄── tenanta :8082   aud pt-default-via-tenanta-aud  (camunda-pt-default-via-tenanta-client)
     ◄── tenantb :8083   aud pt-tenantb-aud              (camunda-pt-tenantb-client)

  PT tenanta   — `assigned: [tenanta]`
     ◄── tenanta :8082   aud pt-tenanta-aud              (camunda-pt-tenanta-client)

  PT tenantb   — `assigned: [tenantb]`
     ◄── tenantb :8083   aud pt-tenantb-aud              (camunda-pt-tenantb-client)
```

How the realms are shared:
- **tenanta realm `:8082`** is assigned to **two** PTs — `default` and `tenanta` — isolated by
  **different audiences** (`pt-default-via-tenanta-aud` vs `pt-tenanta-aud`). Same issuer, the
  `aud` claim picks the PT. ← the "one IdP, two PTs, two audiences" case.
- **tenantb realm `:8083`** feeds both `default` (inherited) and `tenantb` with the **same**
  audience (`pt-tenantb-aud`) → those two are isolated by **path** only, not audience.
- **default realm `:8081`** serves only `default` (`pt-default-aud`). `default`'s surface is also
  the cluster `/v2` (`/v2` ≡ `/physical-tenants/default`).

**Caveat / backup slide:** D's matrix is narrower than base **Scenario A**, which uniquely shows
- **audience isolation** (same realm, wrong `aud` → 401 — the `dvta` cells), and
- the **`/v2` ≡ `/physical-tenants/default` identity**.

Keep A's `#54730` cross-issuer cell + the `/v2 ≡ /pt/default` cell on a backup slide to explain the
mechanism underneath D. Run D live; A is conceptual backup only.

---

## 2. The OC ↔ CSL interface (how per-PT chains get set up)

Show these three pieces, in order:

| Layer | What | Where |
|---|---|---|
| **Contract (CSL)** | `CamundaSecurityScopeProvider` SPI → emits one `ScopedSecurityDescriptor` per scope | `camunda-security-library-api` jar (alpha31) |
| **OC adapter** ⭐ | [`PhysicalTenantScopeProvider`](authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantScopeProvider.java) — turns `camunda.physical-tenants.*` config into descriptors | [`…/pt/PhysicalTenantScopeProvider.java`](authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantScopeProvider.java) |
| **CSL consumer** | `ScopedSecurityChainRegistrar` — builds one `SecurityFilterChain` per descriptor, matching `/physical-tenants/<id>/...` | CSL |

⭐ = the money shot: the concrete OC→CSL adapter, in-repo. Each descriptor carries the PT's
issuer/audience/provider selection; CSL builds a JWT decoder scoped to exactly that.

---

## 3. Running it live (Scenario D)

```bash
# Terminal 1 — ES + 3 Keycloak realms (:8081 default, :8082 tenanta, :8083 tenantb)
./pt-smoke-test-idp.sh

# Terminal 2 — OC under the D overlay
./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-two-tenants

# Terminal 3 — the PASS/FAIL assertion matrix (this is the live moment)
./pt-smoke-test-api-two-tenants.sh
```

### ⚠️ Pre-warm before the meeting
The infra boot is the slow, risky part: 3 Keycloak containers + ES, and a first run pulls ~2.5 GB of
images. **Boot Terminals 1 & 2 ahead of time** so that, live, you only run Terminal 3 (the matrix
with the satisfying PASS output). Removes nearly all live-demo risk.

### Pre-flight checklist
- [ ] Docker ≥ 4 GB free; images pulled (`keycloak:26.2`, `elasticsearch:8.19.13`)
- [ ] Free ports: 8080 (OC), 8081/8082/8083 (Keycloak), 9200 (ES), 9600 (OC mgmt)
- [ ] Java 21 on `PATH`; `curl`, `jq`, `python3` available
- [ ] Terminal 1 shows `=== PT smoke-test local IdPs + ES ready ===`
- [ ] Terminal 2 shows `Tomcat started on port 8080` (logs tee to `/tmp/oc.log`)
- [ ] Dry-run Terminal 3 once before the meeting (expect exit 0 / all PASS)

---

## The story in one line

OC's [`PhysicalTenantScopeProvider`](authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantScopeProvider.java) reads the PT config → emits a descriptor per tenant → CSL's
registrar builds a per-tenant, issuer/audience-scoped filter chain → a tenant's token is accepted on
its own `/physical-tenants/<id>/...` path and **401 on every other tenant's**.
