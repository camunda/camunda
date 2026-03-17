# Authorization

This document describes the authorization model used in Camunda's Orchestration Cluster. It covers the core concepts, data structures, and how they relate to each other.

## Overview

Authorization in Camunda controls **who** can perform **what action** on **which resource**. The model is built around three core concepts:

1. **Resource Types** -- the kind of entity being protected (e.g. a process definition, a user task, a tenant)
2. **Permission Types** -- the action being performed (e.g. read, create, update, delete)
3. **Resource Scoping** -- which specific resources the permission applies to (by ID, by property, or wildcard)

These are combined into an `Authorization` record that represents a single access rule: _"this permission type is granted on this resource type for these specific resources."_

## Core Data Structures

### Authorization Record

**Location:** `security/security-core/.../security/auth/Authorization.java`

The `Authorization<T>` record is the central type. It binds together:

|          Field          |            Type             |                           Description                            |                                     Example                                     |
|-------------------------|-----------------------------|------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `resourceType`          | `AuthorizationResourceType` | The kind of resource being protected                             | `PROCESS_DEFINITION`, `USER_TASK`, `TENANT`                                     |
| `permissionType`        | `PermissionType`            | The action being authorized                                      | `READ_PROCESS_INSTANCE`, `CLAIM_USER_TASK`, `CREATE`                            |
| `resourceIds`           | `List<String>`              | Specific resource IDs access is granted to                       | `["my-process-id"]` for one process, `["*"]` for all                            |
| `resourceIdSupplier`    | `Function<T, String>`       | Extracts a resource ID from a domain object at runtime           | `ProcessInstanceRecord::getBpmnProcessId`                                       |
| `resourcePropertyNames` | `Set<String>`               | Property-based authorization (e.g. task assignee)                | `{"assignee"}`, `{"candidateUsers", "candidateGroups"}`                         |
| `condition`             | `Predicate<T>`              | Optional predicate that must hold for the authorization to apply | `withCondition(al -> al.processDefinitionId() != null)`                         |
| `transitive`            | `boolean`                   | Whether this authorization is inherited transitively             | `true` for process-definition permissions that apply to child process instances |

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
| `IS_CAMUNDA_USERS_ENABLED`  | Whether Camunda-managed users are enabled            |
| `IS_CAMUNDA_GROUPS_ENABLED` | Whether Camunda-managed groups are enabled           |

These keys are used to attach identity information to requests so that downstream authorization checks can evaluate permissions.

## Resource Types

**Location:** `security/security-protocol/.../AuthorizationResourceType.java`

Each resource type declares the set of permission types it supports. The full list:

|           Resource Type            |                                                                                                               Supported Permissions                                                                                                                |
|------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AUDIT_LOG`                        | READ                                                                                                                                                                                                                                               |
| `AUTHORIZATION`                    | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                       |
| `BATCH`                            | CREATE, CREATE_BATCH_OPERATION_* (various), READ, UPDATE                                                                                                                                                                                           |
| `CLUSTER_VARIABLE`                 | CREATE, DELETE, UPDATE, READ                                                                                                                                                                                                                       |
| `COMPONENT`                        | ACCESS                                                                                                                                                                                                                                             |
| `DECISION_DEFINITION`              | CREATE_DECISION_INSTANCE, READ_DECISION_DEFINITION, READ_DECISION_INSTANCE, DELETE_DECISION_INSTANCE                                                                                                                                               |
| `DECISION_REQUIREMENTS_DEFINITION` | READ                                                                                                                                                                                                                                               |
| `DOCUMENT`                         | CREATE, READ, DELETE                                                                                                                                                                                                                               |
| `EXPRESSION`                       | EVALUATE                                                                                                                                                                                                                                           |
| `GLOBAL_LISTENER`                  | CREATE_TASK_LISTENER, READ_TASK_LISTENER, UPDATE_TASK_LISTENER, DELETE_TASK_LISTENER                                                                                                                                                               |
| `GROUP`                            | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                       |
| `MAPPING_RULE`                     | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                       |
| `MESSAGE`                          | CREATE, READ                                                                                                                                                                                                                                       |
| `PROCESS_DEFINITION`               | CREATE_PROCESS_INSTANCE, CLAIM_USER_TASK, READ_PROCESS_DEFINITION, READ_PROCESS_INSTANCE, READ_USER_TASK, UPDATE_PROCESS_INSTANCE, UPDATE_USER_TASK, MODIFY_PROCESS_INSTANCE, COMPLETE_USER_TASK, CANCEL_PROCESS_INSTANCE, DELETE_PROCESS_INSTANCE |
| `RESOURCE`                         | CREATE, READ, DELETE_DRD, DELETE_FORM, DELETE_PROCESS, DELETE_RESOURCE                                                                                                                                                                             |
| `ROLE`                             | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                       |
| `SYSTEM`                           | READ, READ_USAGE_METRIC, READ_JOB_METRIC, UPDATE                                                                                                                                                                                                   |
| `TENANT`                           | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                       |
| `USER`                             | CREATE, READ, UPDATE, DELETE                                                                                                                                                                                                                       |
| `USER_TASK`                        | READ, UPDATE, CLAIM, COMPLETE                                                                                                                                                                                                                      |

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

The `Authorization` record provides a fluent builder API. A typical authorization is constructed like:

```java
Authorization.of(b -> b
    .processDefinition()           // resource type
    .readProcessInstance()         // permission type
    .resourceId("my-process-id")  // scoped to a specific resource
);
```

Or for property-based user task authorization:

```java
Authorization.of(b -> b
    .userTask()
    .readUserTask()
    .authorizedByAssignee()
    .or()
    .authorizedByCandidateUsers()
    .or()
    .authorizedByCandidateGroups()
);
```

## Where Authorization Checks Are Applied

Authorization checks happen in two places in the codebase, corresponding to the two storage layers:

1. **[Engine Authorization](engine-authorization.md)** -- checks performed in the Zeebe stream processor before commands are written to RocksDB (primary storage). This is the pre-execution gate that prevents unauthorized state mutations.

2. **[REST Layer Authorization](rest-authorization.md)** -- checks performed in the REST API layer against Elasticsearch/OpenSearch (secondary storage). This handles search result filtering, pre-validation of actions before forwarding to the engine, and permission collection for UI feature toggling.

Both layers use the same underlying authorization model described on this page, but they differ in how they operate:

|         Aspect          |                        Engine (`AuthorizationCheckBehavior`)                        |                REST (`AuthorizationChecker`)                |
|-------------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------------|
| **When**                | Before command processing                                                           | At query time / before forwarding to engine                 |
| **Data source**         | RocksDB (primary, strongly consistent)                                              | Elasticsearch/OpenSearch (secondary, eventually consistent) |
| **Identity resolution** | Extracts from command claims, resolves groups/roles/mapping rules from engine state | Receives pre-resolved `CamundaAuthentication`               |
| **Tenant checks**       | Built-in multi-tenancy support                                                      | Handled separately (not in this class)                      |
| **Caching**             | Guava LoadingCache with configurable TTL                                            | No caching (relies on search index performance)             |
| **Property-based auth** | Three-step cascade with property evaluators                                         | Returns property scopes for upstream filtering              |
| **Internal commands**   | Can bypass checks for engine-internal commands                                      | Not applicable                                              |
| **Primary use**         | Gate state mutations                                                                | Filter search results and pre-validate actions              |

See the individual pages for details.

## Relationship to Identity Model

Authorizations are granted to identity entities (users, roles, groups, mapping rules, clients). See [Identity documentation](../misc/data-model.md) for the full data model. The key relationships are:

- A **User**, **Role**, **Group**, **Mapping Rule**, or **Client** can be granted one or more `Authorization` records.
- Each `Authorization` contains one or more `Permission` entries scoped to specific resources.
- Roles and groups provide indirect authorization -- a user inherits permissions from their assigned roles and group memberships.

