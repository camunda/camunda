# Identity Data Model

## At a glance

- This is the runtime Identity model used by Orchestration Cluster Identity.
- `User` means a human identity in runtime IAM (username-based principal).
- The model exists in three related layers: record model, primary storage model, and secondary storage model.
- Commands are expressed as records, applied to primary storage, and then projected to secondary storage for queries.

## Model layers

| Layer | Purpose | Representative packages / symbols |
| --- | --- | --- |
| Record model | Command/event payloads written to the Zeebe log | `io.camunda.zeebe.protocol.impl.record.value.user.UserRecord`, `io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord`, `io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord`, `io.camunda.zeebe.protocol.impl.record.value.authorization.*` |
| Primary storage model | Source of truth for state transitions in Zeebe (RocksDB) | `io.camunda.zeebe.engine.state.user.DbUserState`, `io.camunda.zeebe.engine.state.tenant.DbTenantState`, `io.camunda.zeebe.engine.state.authorization.*` |
| Secondary storage model | Query projection in ES/OS/RDBMS | `io.camunda.search.entities.UserEntity`, `io.camunda.search.entities.GroupEntity`, `io.camunda.search.entities.RoleEntity`, `io.camunda.search.entities.TenantEntity`, `io.camunda.search.entities.AuthorizationEntity`, `io.camunda.search.entities.MappingRuleEntity` |

## Simplified view

### Cross-layer flow

This diagram shows the high-level data path across layers:

- Records represent intent in the Zeebe log.
- Primary storage applies records as source-of-truth state transitions.
- Secondary storage receives projected data for query/read workloads via exporters.

```mermaid
flowchart LR
  R[Record model</br>log payloads]
  P[Primary model</br>RocksDB state]
  S[Secondary model</br>query projection]

  R -->|apply| P -->|export| S
```

### Relationship view (conceptual, shared vocabulary)

This diagram shows the shared business vocabulary and its main relationships:

- `Tenant` is the top-level scope boundary.
- `User` and `Client` are principals that can be grouped and assigned roles.
- `MappingRule` represents a claim name and value pair from a JWT Authentication Token
- `Authorization` represents grants owned by principals, groups, roles, or mapping rules.
- The view is intentionally simplified (no storage-specific cardinalities or index details).

```mermaid
flowchart TD
  Tenant[Tenant]
  Role[Role]
  Group[Group]
  User[User]
  Client[Client]
  MappingRule[MappingRule]
  Authorization[Authorization]

  Tenant -->|scope| Role
  Tenant -->|scope| Group
  Tenant -->|scope| User
  Tenant -->|scope| Client
  Tenant -->|scope| MappingRule

  Group -->|assigns| Role
  User -->|member of| Group
  Client -->|member of| Group
  User -->|assigned| Role
  Client -->|assigned| Role
  MappingRule -->|maps to| Group
  MappingRule -->|maps to| Role

  Role -->|grants| Authorization
  Group -->|grants| Authorization
  User -->|grants| Authorization
  Client -->|grants| Authorization
  MappingRule -->|grants| Authorization
```

## Detailed view by layer

### 1) Record model (log payload shape)

- Focus: command and event payloads, not query-optimized documents.
- Common records for IAM entities: `UserRecord`, `GroupRecord`, `RoleRecord`, `TenantRecord`, `MappingRuleRecord`, `AuthorizationRecord`.

```mermaid
erDiagram
  UserRecord {
    long userKey
    string username
    string name
    string email
    string password
  }
  GroupRecord {
    long groupKey
    string groupId
    string name
    string description
  }
  RoleRecord {
    long roleKey
    string roleId
    string name
    string description
  }
  TenantRecord {
    long tenantKey
    string tenantId
    string name
    string description
  }
  MappingRuleRecord {
    long mappingRuleKey
    string mappingRuleId
    string claimName
    string claimValue
    string name
  }
  AuthorizationRecord {
    long authorizationKey
    string ownerId
    enum ownerType
    enum resourceType
    Permission[] permissions
  }
```

### 2) Primary storage model (RocksDB state)

- Focus: engine state optimized for deterministic command processing and key-based lookups.
- Backed by Zeebe state implementations in `io.camunda.zeebe.engine.state.*`.
- Entity relations are represented through state entries/indices (for example memberships and assignments), not as one relational schema.

```mermaid
erDiagram
  User {
    long userKey
    string username PK
    string name
    string email
    string password
  }
  Role {
    long roleKey
    string id PK
    string name
    string description
  }
  Group {
    long groupKey
    string id PK
    string name
    string description
  }
  Tenant {
    long tenantKey
    string id PK
    string name
    string description
  }
  MappingRule {
    long mappingRuleKey
    string id PK
    string name
    string claimName
    string claimValue
  }
  Authorization {
    long authorizationKey
    string ownerId
    enum ownerType
    enum resourceType
    string resourceId
    string resourcePropertyName
  }
  Permission {
    enum permissionType
    string[] resourceIds
  }

  MappingRule }o..o{ Tenant: "assigned"
  User ||--o{ Authorization: "granted"
  Authorization ||--|{ Permission: "granted"
  Group }o..o{ Tenant: "assigned"
  Group }o..o{ Role: "assigned"
  Group ||--o{ Authorization: "granted"
  Role ||--o{ Authorization: "granted"
  User }o..o{ Group: "member"
  User }o..o{ Role: "assigned"
  User }o..o{ Tenant: "assigned"
  MappingRule ||--o{ Authorization: "granted"
  MappingRule }o..o{ Role: "assigned"
  MappingRule }o..o{ Group: "member"
  Role }o--o{ Tenant: "assigned"
```

### 3) Secondary storage model (search projection)

- Focus: query-ready documents/rows in ES/OS/RDBMS.
- Backed by `io.camunda.search.entities.*` records such as `UserEntity`, `RoleEntity`, `TenantEntity`, `AuthorizationEntity`, `MappingRuleEntity`.
- Relationship between entities is different, depending on ES/OS DB vs RDBMS.

```mermaid
erDiagram
  UserEntity {
    long userKey
    string username
    string name
    string email
    string password
  }
  GroupEntity {
    long groupKey
    string groupId
    string name
    string description
  }
  RoleEntity {
    long roleKey
    string roleId
    string name
    string description
  }
  TenantEntity {
    long key
    string tenantId
    string name
    string description
  }
  MappingRuleEntity {
    long mappingRuleKey
    string mappingRuleId
    string claimName
    string claimValue
    string name
  }
  AuthorizationEntity {
    long authorizationKey
    string ownerId
    string ownerType
    string resourceType
    string resourceId
    string resourcePropertyName
    PermissionType[] permissionTypes
  }
```

## Primary vs secondary differences

| Concern | Primary storage model | Secondary storage model                |
| --- | --- |----------------------------------------|
| Role in architecture | Source of truth for command handling | Read model for query APIs              |
| Write path | Updated by command processing in engine | Updated by exporter pipeline           |
| Read pattern | Key/state access in engine internals | Filter/sort/search in REST query layer |
| Shape | State-centric, index-driven | Query-centric, document/row oriented   |
| Consistency | Immediate for command processing | Eventually consistent with primary     |
| Typical package | `io.camunda.zeebe.engine.state.*` | `io.camunda.search.entities.*`         |

## Entity cheat sheet

- `User`: human runtime principal, uniquely identified by `username`.
- `Client`: machine principal (service/client id) used for API access.
- `Group`: collection of principals (`User` or `Client`) to manage assignments in bulk.
- `Role`: reusable bundle for authorizations.
- `Tenant`: isolation boundary for runtime resources.
- `MappingRule`: token-claim based mapping from IdP claims to group/role/tenant membership and authorizations.
- `Authorization`: owner-to-resource grant (owner can be `User`, `Client`, `Group`, `Role`, or `MappingRule`).
- `Permission`: action scope within an authorization (for example READ/UPDATE style permissions depending on resource type).

## Unmanaged entities

Under the "simple mapping rule" feature, `User` and `Client` are not lifecycle-managed entities.
Membership and authorization relationships are resolved from the authenticated principal id
(`username` for users, client id for clients) instead of requiring explicit entity persistence first.
