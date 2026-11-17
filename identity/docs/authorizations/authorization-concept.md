# Authorization

This document describes the authorization model used in Camunda's Orchestration Cluster. It covers the core concepts, data structures, and how they relate to each other.

## Overview

Authorization in Camunda controls **who** can perform **what action** on **which resource**. The model is built around three core concepts:

1. **Resource Types** -- the kind of entity being protected (e.g. a process definition, a user task, a tenant)
2. **Permission Types** -- the action being performed (e.g. read, create, update, delete)
3. **Resource Scoping** -- which specific resources the permission applies to (by ID, by property, or wildcard)

These are combined into a `RequiredAuthorization` record that represents a single access rule: _"this permission type is granted on this resource type for these specific resources."_

## Core Data Structures

### RequiredAuthorization Record

**Location:** `io.camunda.security.core.auth.RequiredAuthorization` (defined in CSL's `core` module, not its public `api` package). It is named here despite that, because OC's own code constructs it on every check — a rename in CSL breaks our compile, so these docs would have to be revisited regardless. See the [naming convention](../architecture.md#csl-extension-points-and-ocs-adapters) for CSL types this document does *not* name.

The `RequiredAuthorization<T>` record is the central type. The fields OC code actually varies when
declaring a check:

|          Field          |            Type             |                      Description                       |
|-------------------------|-----------------------------|--------------------------------------------------------|
| `resourceType`          | `AuthorizationResourceType` | The kind of resource being protected                   |
| `permissionType`        | `PermissionType`            | The action being authorized                            |
| `resourceIds`           | `List<String>`              | Specific resource IDs access is granted to, or `["*"]` |
| `resourceIdSupplier`    | `Function<T, String>`       | Extracts a resource ID from a domain object at runtime |
| `resourcePropertyNames` | `Set<String>`               | Property-based authorization (e.g. task assignee)      |

The record also carries `condition` (a `Predicate<T>` that must hold for the authorization to apply)
and `transitive` (whether the authorization is inherited, as process-definition permissions are by
child process instances). For the full record see the
[CSL architecture documentation](https://github.com/camunda/camunda-security-library/blob/main/docs/architecture/05-building-block-view.md).

`resourceType` is CSL's `io.camunda.security.api.model.authz.AuthorizationResourceType`. The
[Resource Types](#resource-types) section below documents the local protocol enum of the same
name (`security/security-protocol/...`) -- these are distinct types.

An authorization can grant access in two ways:
- **By resource ID** -- the user has access to specific resource instances (identified by ID), or to all instances via the wildcard `*`.
- **By resource property** -- the user has access because a property of the resource matches them (e.g. they are the assignee of a user task).

### Authorization Context Keys

**Location:** `zeebe/auth/.../zeebe/auth/Authorization.java`

A simpler class that defines constant keys used to propagate authentication context through the system:

|          Constant           |                       Purpose                        |
|-----------------------------|------------------------------------------------------|
| `AUTHORIZED_ANONYMOUS_USER` | Marks the request as coming from an anonymous user   |
| `AUTHORIZED_TENANTS`        | The tenants the authenticated user is authorized for |
| `AUTHORIZED_USERNAME`       | The authenticated user's username                    |
| `AUTHORIZED_CLIENT_ID`      | The authenticated client's ID                        |
| `USER_TOKEN_CLAIMS`         | Claims extracted from the user's token               |
| `USER_GROUPS_CLAIMS`        | Group claims extracted from the user's token         |

These keys are used to attach identity information to requests so that downstream authorization checks can evaluate permissions.

## Resource Types

**Location:** `security/security-protocol/.../AuthorizationResourceType.java`

Each resource type declares the set of permission types it supports. The full list:

|           Resource Type            |                                                                                                                            Supported Permissions                                                                                                                             |
|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AUDIT_LOG`                        | READ                                                                                                                                                                                                                                                                         |
| `AUTHORIZATION`                    | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                                                 |
| `BACKUP`                           | CREATE, READ, DELETE, RESTORE                                                                                                                                                                                                                                                |
| `BATCH`                            | CREATE, CREATE_BATCH_OPERATION_* (various), READ, UPDATE                                                                                                                                                                                                                     |
| `CLUSTER_VARIABLE`                 | CREATE, DELETE, UPDATE, READ                                                                                                                                                                                                                                                 |
| `COMPONENT`                        | ACCESS                                                                                                                                                                                                                                                                       |
| `DECISION_DEFINITION`              | CREATE_DECISION_INSTANCE, READ_DECISION_DEFINITION, READ_DECISION_INSTANCE, DELETE_DECISION_INSTANCE                                                                                                                                                                         |
| `DECISION_REQUIREMENTS_DEFINITION` | READ                                                                                                                                                                                                                                                                         |
| `DOCUMENT`                         | CREATE, READ, DELETE                                                                                                                                                                                                                                                         |
| `EXPORTER`                         | PAUSE                                                                                                                                                                                                                                                                        |
| `EXPRESSION`                       | EVALUATE                                                                                                                                                                                                                                                                     |
| `GLOBAL_LISTENER`                  | CREATE_TASK_LISTENER, READ_TASK_LISTENER, UPDATE_TASK_LISTENER, DELETE_TASK_LISTENER                                                                                                                                                                                         |
| `GROUP`                            | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                                                 |
| `MAPPING_RULE`                     | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                                                 |
| `MESSAGE`                          | CREATE, READ                                                                                                                                                                                                                                                                 |
| `PROCESS_DEFINITION`               | CREATE_PROCESS_INSTANCE, CLAIM_USER_TASK, READ_PROCESS_DEFINITION, READ_PROCESS_INSTANCE, READ_USER_TASK, UPDATE_PROCESS_INSTANCE, UPDATE_USER_TASK, MODIFY_PROCESS_INSTANCE, COMPLETE_USER_TASK, CANCEL_PROCESS_INSTANCE, DELETE_PROCESS_INSTANCE, SUSPEND_PROCESS_INSTANCE |
| `RESOURCE`                         | CREATE, READ, DELETE_DRD, DELETE_FORM, DELETE_PROCESS, DELETE_RESOURCE                                                                                                                                                                                                       |
| `ROLE`                             | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                                                 |
| `SECRET`                           | READ, REVEAL                                                                                                                                                                                                                                                                 |
| `SYSTEM`                           | READ, READ_USAGE_METRIC, READ_JOB_METRIC, UPDATE                                                                                                                                                                                                                             |
| `TENANT`                           | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                                                 |
| `USER`                             | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                                                 |
| `USER_TASK`                        | READ, UPDATE, CLAIM, COMPLETE                                                                                                                                                                                                                                                |

The special value `UNSPECIFIED` exists as an internal default to catch cases where a resource type was not set.

## Authorization Scope

**Location:** `security/security-protocol/.../AuthorizationScope.java`

`AuthorizationScope` defines how a permission is scoped to specific resources:

- **Wildcard (`*`)** -- access to all resources of that type. Uses `AuthorizationResourceMatcher.ANY`.
- **By ID** -- access to a specific resource instance, identified by its ID. Uses `AuthorizationResourceMatcher.ID`.
- **By Property** -- access based on a resource property name (e.g. `assignee`, `candidateUsers`, `candidateGroups` for user tasks). Uses `AuthorizationResourceMatcher.PROPERTY`.

## Property-Based Authorization

For user tasks, authorization can be granted based on the relationship between the authenticated user and the task:

|     Property     |        Constant         |                       Meaning                       |
|------------------|-------------------------|-----------------------------------------------------|
| Assignee         | `PROP_ASSIGNEE`         | The user is assigned to this task                   |
| Candidate Users  | `PROP_CANDIDATE_USERS`  | The user is a candidate for this task               |
| Candidate Groups | `PROP_CANDIDATE_GROUPS` | The user belongs to a candidate group for this task |

This allows fine-grained access control where users can only see and act on tasks they are directly involved with, without needing explicit per-task authorization rules.

## How Authorizations Are Built

The `RequiredAuthorization` record provides a fluent builder API. A typical authorization is constructed like:

```java
RequiredAuthorization.of(b -> b
    .processDefinition()           // resource type
    .readProcessInstance()         // permission type
    .resourceId("my-process-id")  // scoped to a specific resource
);
```

Property-based user task authorization is declared the same way, via
`authorizedByAssignee()` / `authorizedByCandidateUsers()` / `authorizedByCandidateGroups()`.

## Where Authorization Checks Are Applied

Authorization checks happen in two places in the codebase, corresponding to the two storage layers:

1. **[Engine Authorization](engine-authorization.md)** -- checks performed in the Zeebe stream processor before commands are written to RocksDB (primary storage). This is the pre-execution gate that prevents unauthorized state mutations.

2. **[REST Layer Authorization](rest-authorization.md)** -- checks performed in the REST API layer against Elasticsearch/OpenSearch (secondary storage). This handles search result filtering, pre-validation of actions before forwarding to the engine, and permission collection for UI feature toggling.

Both layers use the same underlying authorization model described on this page, and both hand the
decision itself to the **same CSL check** -- neither implements its own evaluation. What differs is
the data that check reads, how the check instance is obtained, and where the OC-side entry point
sits:

|           Aspect           |                 Engine (`CslAuthorizationCheck` / `CslTenantCheck`)                 |                                REST (`DefaultResourceAccessProvider`)                                 |
|----------------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| **Authorization decision** | The CSL check, reached through CSL's `AuthorizationCheckPort`                       | The same CSL check, constructed per physical tenant so it reads that tenant's index                   |
| **When**                   | Before command processing                                                           | At query time / before forwarding to engine                                                           |
| **Data source**            | RocksDB (primary, strongly consistent), via `AuthorizationScopeStateAdapter`        | Elasticsearch/OpenSearch (secondary, eventually consistent), via `SearchAuthorizationScopeRepository` |
| **Identity resolution**    | Extracts from command claims, resolves groups/roles/mapping rules from engine state | Receives pre-resolved `CamundaAuthentication`                                                         |
| **Tenant checks**          | Built-in multi-tenancy support                                                      | Handled separately, outside this check                                                                |
| **Caching**                | Caffeine LoadingCache with configurable TTL, held by the engine's port adapters     | No caching (relies on search index performance)                                                       |
| **Property-based auth**    | Evaluated by CSL; OR-composition for user tasks done locally                        | Returns property scopes for upstream filtering                                                        |
| **Internal commands**      | Can bypass checks for engine-internal commands                                      | Not applicable                                                                                        |
| **Primary use**            | Gate state mutations                                                                | Filter search results and pre-validate actions                                                        |

See the individual pages for details.

## Relationship to Identity Model

Authorizations are granted to identity entities (users, roles, groups, mapping rules, clients). See [Identity documentation](../references/data-model.md) for the full data model. The key relationships are:

- A **User**, **Role**, **Group**, **Mapping Rule**, or **Client** can be granted one or more `Authorization` records.
- Each `Authorization` contains one or more `Permission` entries scoped to specific resources.
- Roles and groups provide indirect authorization -- a user inherits permissions from their assigned roles and group memberships.

