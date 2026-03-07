# ADR-0003: Persistence Abstraction with Read/Write Separation

**Date:** 2025-03
**Status:** Accepted

## Context

The auth library needs to persist several categories of data:

|     Category      |                      Examples                       |                 Needs writes?                  |
|-------------------|-----------------------------------------------------|------------------------------------------------|
| Sessions          | Web session state (id, attributes, expiry)          | Yes — library creates/updates/deletes sessions |
| Token audit       | Token exchange records (exchangeId, scopes, status) | Yes — library records exchanges                |
| Users             | Username, email, password hash                      | Depends on deployment mode                     |
| Roles + members   | Role definitions, user-to-role assignments          | Depends on deployment mode                     |
| Tenants + members | Tenant definitions, user-to-tenant assignments      | Depends on deployment mode                     |
| Groups + members  | Group definitions, user-to-group assignments        | Depends on deployment mode                     |
| Mapping rules     | OIDC claim-to-role/group/tenant mappings            | Depends on deployment mode                     |
| Authorizations    | Permission records (owner, resource, permissions)   | Depends on deployment mode                     |

"Depends on deployment mode" is the key insight. The library has two consumers with fundamentally
different write paths:

1. **Orchestration Cluster (OC):** Zeebe is the source of truth. Writes go through Zeebe's command
   processor, then Zeebe exporters propagate data to Elasticsearch or RDBMS. The auth library
   must **only read** from the secondary storage — writing directly would cause dual-write
   inconsistencies.

2. **Camunda Hub:** No Zeebe. The auth library is the sole owner of persistence. It must handle
   **both reads and writes** directly to the chosen backend (initially RDBMS, with room for
   enhancement like outbox-pattern for downstream replicas).

Additionally, the monorepo currently selects between Elasticsearch and RDBMS via a single
deployment-wide property (`camunda.data.secondary-storage.type`). There is **no scenario** where
a single deployment uses both ES and RDBMS simultaneously for auth data.

### What exists today

The auth library already has two persistence SPIs:

- `SessionPersistencePort` — CRUD for web sessions
- `TokenStorePort` — CRUD for token exchange audit records

Both have implementations in `auth-persist-elasticsearch` and `auth-persist-rdbms`. However:

- Identity entities (users, roles, tenants, groups, mapping rules, authorizations) have **no SPIs**
  in the auth library. Their persistence is scattered across the monorepo's `search/`, `db/rdbms/`,
  `webapps-schema/`, and `dist/` modules.
- There is no mechanism to distinguish between "library owns writes" and "external system owns
  writes."

## Decision

### 1. Two configuration properties

```properties
# Which storage backend to use
camunda.auth.persistence.type=elasticsearch|rdbms

# Who controls writes to the storage
camunda.auth.persistence.mode=standalone|external
```

**`standalone`** — The library owns all reads AND writes. The persistence store is the source of
truth. This is Hub's mode.

**`external`** — An external system populates the storage (e.g., Zeebe exporters). The library
only reads. This is OC's mode.

The library does not know or care what the external system is. It only knows whether it owns
writes.

For the monorepo, a property bridge maps the existing `camunda.data.secondary-storage.type` to
`camunda.auth.persistence.type` and hardcodes `mode=external`, requiring zero new configuration.

### 2. Separate read and write SPIs

Each entity category gets two interfaces:

```java
// Read port — always activated, regardless of mode
public interface UserReadPort {
    Optional<AuthUser> findByUsername(String username);
    Optional<AuthUser> findByKey(long userKey);
}

// Write port — only activated when mode=standalone
public interface UserWritePort {
    void save(AuthUser user);
    void deleteByUsername(String username);
}
```

The full set of persistence SPIs:

|              Read port              |        Write port        |     Domain model      |
|-------------------------------------|--------------------------|-----------------------|
| `UserReadPort`                      | `UserWritePort`          | `AuthUser`            |
| `RoleReadPort`                      | `RoleWritePort`          | `AuthRole`            |
| `TenantReadPort`                    | `TenantWritePort`        | `AuthTenant`          |
| `GroupReadPort`                     | `GroupWritePort`         | `AuthGroup`           |
| `MappingRuleReadPort`               | `MappingRuleWritePort`   | `AuthMappingRule`     |
| `AuthorizationReadPort`             | `AuthorizationWritePort` | `AuthorizationRecord` |
| `SessionPersistencePort` (existing) | (combined read/write)    | `SessionData`         |
| `TokenStorePort` (existing)         | (combined read/write)    | `TokenMetadata`       |

Sessions and token audit remain combined read/write because the library always owns these
writes, regardless of mode. The library creates and manages sessions; the library records
token exchanges. No external system writes these.

### 3. Read and write ports live in `auth-domain`

Both read and write ports are defined in `auth-domain/port/outbound/`. The domain module
defines the contracts; the persist modules provide implementations.

Domain models (`AuthUser`, `AuthRole`, etc.) are records in `auth-domain/model/`. They are
owned by the library — independent of monorepo-specific entity classes like
`io.camunda.search.entities.UserEntity`.

### 4. Each persist module provides both read and write adapters

`auth-persist-elasticsearch` provides:
- `ElasticsearchUserReadAdapter`, `ElasticsearchUserWriteAdapter`
- `ElasticsearchRoleReadAdapter`, `ElasticsearchRoleWriteAdapter`
- ... and so on for each entity

`auth-persist-rdbms` provides the equivalent MyBatis-backed adapters.

### 5. Auto-configuration wires beans based on mode

```java
// In each persist module's auto-configuration:

// Always created
@Bean
@ConditionalOnMissingBean
public UserReadPort userReadPort(...) { ... }

// Only when library owns writes
@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(name = "camunda.auth.persistence.mode", havingValue = "standalone")
public UserWritePort userWritePort(...) { ... }
```

When `mode=external`, write port beans are not created. If a consumer needs to write through
the library for some reason (e.g., session management always writes), the specific port's
auto-config handles that independently.

When `mode=standalone`, consumers can still override any write port by providing their own bean
(e.g., Hub adding an outbox decorator):

```java
@Bean
@Primary
public UserWritePort outboxUserWritePort(UserWritePort delegate, OutboxPublisher outbox) {
    return new OutboxDecoratingUserWritePort(delegate, outbox);
}
```

### 6. Membership resolution uses read ports

The existing `MembershipResolver` SPI in auth-domain is the consumer-facing contract for
resolving a principal's memberships. In `standalone` mode, the default implementation queries
`RoleReadPort`, `TenantReadPort`, and `GroupReadPort` directly. In `external` mode, the
monorepo can provide its own `MembershipResolver` that delegates to its existing service layer.

## Consequences

- **Positive:** A single property switch (`mode=standalone|external`) adapts the library to
  fundamentally different deployment models without code changes.
- **Positive:** The library never knows about Zeebe, exporters, or any specific consumer
  infrastructure.
- **Positive:** Hub gets full CRUD out of the box. OC gets read-only, with writes handled by
  its existing Zeebe pipeline.
- **Positive:** The outbox pattern, SCIM sync, or any future write enhancement is a decorator
  on the write port — no library changes needed.
- **Positive:** `@ConditionalOnMissingBean` on all beans means consumers can override any
  default at any level.
- **Negative:** More interfaces to define and implement (12 read/write port pairs + domain models).
- **Negative:** Maintaining two persist modules (ES + RDBMS) for each port doubles the adapter code.
- **Risk:** Schema drift — the library's ES index mappings or RDBMS tables must stay compatible
  with what Zeebe exporters produce (in `external` mode). This requires coordination between
  the auth library and the exporter teams.

