# Authorization Layer Documentation

> **Note:** This documentation serves as the central reference for Camunda's identity and authorization features. While currently focused on conditional and transitive authorizations, this document will expand to cover all authorization and identity-related features in the future.

## Overview

Camunda's authorization layer provides fine-grained access control for resources within the platform. The authorization system supports standard resource-based permissions as well as advanced features like **conditional authorizations** and **transitive authorizations** that enable more sophisticated access control patterns.

## Core Concepts

### Authorization Structure

An `Authorization` consists of:
- **Resource Type**: The type of resource being protected (e.g., `PROCESS_DEFINITION`, `USER_TASK`, `AUDIT_LOG`)
- **Permission Type**: The operation being authorized (e.g., `READ`, `CREATE_PROCESS_INSTANCE`, `UPDATE_USER_TASK`)
- **Resource IDs**: List of resource IDs the user has permission to access (populated from user's permissions during pre-filtering, used to filter queries)
- **Resource ID Supplier**: Function to extract the resource ID from a document during post-filtering (used to compare against user's permitted resource IDs)
- **Condition**: Optional predicate to determine if authorization applies to a specific document (**only evaluated in post-filtering**)
- **Transitive Flag**: Controls whether wildcard permissions disable authorization checks (for cross-resource authorization patterns)

### Authorization Conditions

The `AuthorizationCondition` interface allows expressing how multiple authorizations should be evaluated:

- **Single Authorization**: Wraps a single authorization requirement
- **AnyOf Authorization**: Disjunctive condition where access is granted if any of the child authorizations are satisfied

### Resource IDs vs Resource ID Supplier

These two fields serve distinct purposes in different phases of authorization:

#### Resource IDs (`resourceIds`)

- **Used in:** Pre-filtering (search operations)
- **Purpose:** Contains the list of resource IDs the user has permission to access
- **Population:** Automatically populated during authorization resolution by querying the user's permissions from the database
- **Usage:** Passed to the search backend to filter queries (e.g., `processDefinitionId IN ['proc-1', 'proc-2']`)

**Example flow:**

```
1. User requests search with authorization
2. System queries: "What PROCESS_DEFINITION IDs does this user have READ permission on?"
3. Result: ['proc-1', 'proc-2', 'proc-3']
4. Authorization.resourceIds is populated with these IDs
5. Backend filters query: WHERE processDefinitionId IN ('proc-1', 'proc-2', 'proc-3')
```

#### Resource ID Supplier (`resourceIdSupplier`)

- **Used in:** Post-filtering (get operations)
- **Purpose:** Extracts the resource ID from the fetched document to check against user's permissions
- **Usage:** Applied to the retrieved document to get its resource ID, which is then compared with the user's permitted resource IDs

**Example flow:**

```
1. User requests getByKey("audit-log-123")
2. Document is fetched: AuditLogEntity{processDefinitionId: "proc-1", ...}
3. resourceIdSupplier.apply(document) → extracts "proc-1"
4. System checks: "Does user have permission for resource ID 'proc-1'?"
5. If yes, return document; if no, throw ResourceAccessDeniedException
```

#### Why Both Are Needed

**Pre-filtering (Search):** We know the user's permissions but don't have documents yet
- Use `resourceIds` (user's permitted IDs) to filter the query

**Post-filtering (Get):** We have the document but need to extract which resource ID it belongs to
- Use `resourceIdSupplier` to extract the ID from the document, then verify permission

#### Practical Example: Audit Log Authorization

Consider this authorization definition:

```java
// Developer writes:
Authorization<AuditLogEntity> auth =
    Authorization.of(a -> a.processDefinition().readProcessInstance())
        .with(AuditLogEntity::processDefinitionId);
        //    ↑ resourceIdSupplier set for extracting ID during post-filtering
```

**During Search Operations (Pre-filtering):**

1. User requests to search audit logs
2. System queries: "What process definitions can this user access?"
3. Database returns: `['proc-1', 'proc-2']`
4. System populates: `auth = auth.with(['proc-1', 'proc-2'])` ← resourceIds filled with user's permissions
5. Backend filters query: `WHERE processDefinitionId IN ('proc-1', 'proc-2')`
6. Result: Only audit logs for processes the user has permission to see

**During Get Operations (Post-filtering):**

1. User requests: `getAuditLog("audit-123")`
2. System fetches: `AuditLogEntity{id: "audit-123", processDefinitionId: "proc-1", ...}`
3. System extracts: `resourceIdSupplier.apply(auditLog)` → returns `"proc-1"`
4. System checks: "Does this user have permission for process definition 'proc-1'?"
5. Decision: If yes, return document; if no, throw `ResourceAccessDeniedException`

**Key Insight:** The same authorization definition serves both purposes:
- The `resourceIdSupplier` (`.with(AuditLogEntity::processDefinitionId)`) extracts the ID during gets
- The `resourceIds` field gets populated with the user's permitted IDs during searches

## Conditional Authorizations

### What are Conditional Authorizations?

Conditional authorizations allow you to define authorization rules that only apply when certain runtime conditions are met. This enables context-aware access control where the applicability of an authorization depends on the properties of the resource being accessed.

**⚠️ Implementation Note:**
- ✅ **Conditions are evaluated in post-filtering** (get operations) where documents are available
- ℹ️ **Conditions are not evaluated in pre-filtering** (search operations) - only resource IDs are used for query filtering
- **By design:** Conditions require actual documents to evaluate, which are only available during get operations

### Use Cases

Conditional authorizations are particularly useful when:
1. **Resource Type Differentiation**: Different authorization rules should apply based on resource characteristics
2. **Category-Based Access**: Access should be granted only for specific categories or types within a resource
3. **Null-Safe Authorization**: Authorization should only apply when certain fields are present

### Implementation

A conditional authorization is created using the `withCondition()` method:

```java
Authorization<AuditLogEntity> authorization =
    Authorization.of(a -> a.processDefinition().readProcessInstance())
        .with(AuditLogEntity::processDefinitionId)
        .withCondition(al -> al.processDefinitionId() != null);
```

### How Conditional Authorizations Work

#### Condition Field Usage

The `condition` field is **only evaluated during post-filtering** (get operations). During pre-filtering (search operations), conditions are intentionally not used.

#### Pre-Filtering (Search Operations)

**By Design:** Conditions are not evaluated during search operations. The search backend uses only the resource IDs for filtering.

**Why:** Search operations don't have documents yet, only query criteria. The condition predicate requires an actual document to evaluate, which is only available in post-filtering after the document has been fetched.

**Behavior:**

```java
// Condition is defined but not evaluated during searches
Authorization.of(a -> a.processDefinition().readProcessInstance())
    .with(AuditLogEntity::processDefinitionId)
    .withCondition(al -> al.processDefinitionId() != null);  // ← Not evaluated in searches

// Search query uses only: resource IDs (user's permitted process definition IDs)
// Search query ignores: the condition (no document to evaluate it against)
```

**Result:** Search results are filtered only by resource IDs. Documents that would fail the condition check may be included in search results, but would be denied access if fetched individually via get.

#### Post-Filtering (Get Operations)

**Fully Implemented:** Conditions are evaluated during get operations to determine which authorizations apply.

**Process:**

1. The resource is fetched without authorization checks
2. For each authorization, the controller calls `appliesTo(document)` to test if the condition passes
3. Only authorizations where `appliesTo(document)` returns `true` are evaluated for access control
4. If no authorizations apply in a **single authorization** context, access is denied with a "not applicable" error
5. In an **AnyOf** context, at least one applicable authorization must grant access

### Example: Audit Log Authorization

The audit log service demonstrates a sophisticated use of conditional authorizations:

```java
private static final AuthorizationCondition AUDIT_LOG_AUTHORIZATIONS =
    AuthorizationConditions.anyOf(
        // Direct audit log read permission for specific categories
        AUDIT_LOG_READ_AUTHORIZATION.with(al -> al.category().name()),

        // Read audit logs via process instance permissions
        AUDIT_LOG_READ_PROCESS_INSTANCE_AUTHORIZATION
            .with(AuditLogEntity::processDefinitionId)
            .withCondition(al -> al.processDefinitionId() != null),

        // Read audit logs via user task permissions
        AUDIT_LOG_READ_USER_TASK_AUTHORIZATION
            .with(AuditLogEntity::processDefinitionId)
            .withCondition(
                al -> al.processDefinitionId() != null
                    && al.category() == AuditLogOperationCategory.USER_TASKS)
    );
```

This configuration allows users to access audit logs through multiple authorization paths:
1. Direct `AUDIT_LOG` read permission for specific categories
2. `PROCESS_DEFINITION` read permissions (only when the audit log has a process definition)
3. `PROCESS_DEFINITION` user task permissions (only for user task operations)

### Key Methods

- **`Authorization.withCondition(Predicate<T> condition)`**: Attaches a condition to an authorization
- **`Authorization.appliesTo(T document)`**: Checks if the authorization's condition allows it to apply to a document
- **`AnyOfAuthorizationCondition.applicableAuthorizations(T document)`**: Filters authorizations to only those that apply to the given document

## Transitive Authorizations

### What are Transitive Authorizations?

The transitive flag is a technical optimization flag that controls how wildcard permissions are handled during search operations. It does **not** cascade or extend permissions from one resource to another.

**What it actually does:**
- Controls whether wildcard (`*`) permissions disable authorization checks during searches
- Enables cross-resource authorization patterns where one resource type's permissions are used to authorize queries on a related resource type

**What it does NOT do:**
- Does NOT automatically grant access to related resources
- Does NOT cascade permissions from parent to child resources
- Does NOT change what resources a user can access (resource IDs still determine access)

### Use Cases

The transitive flag is used when:
1. **Cross-Resource Authorization Patterns**: You want to use process definition permissions to authorize queries on related resources (e.g., audit logs, incidents)
2. **Preventing Wildcard Short-Circuit**: Users with wildcard process definition permissions should still have their queries filtered based on those permissions
3. **Maintaining Query Filtering**: Even with broad permissions, authorization filtering should remain active

### Implementation

A transitive authorization is created using the `transitive()` builder method:

```java
Authorization<AuditLogEntity> transitiveAuth =
    Authorization.of(a -> a.processDefinition().readProcessInstance().transitive());
```

**Important:** This authorization still uses process definition resource IDs to filter audit logs. The transitive flag only prevents wildcard optimization from disabling the check.

### How Transitive Authorizations Work

#### Wildcard Handling

The transitive flag changes how wildcard authorizations are processed:

**Without `transitive` flag:**
- Wildcard authorization (`*`) → Authorization check is **disabled**
- Rationale: User has access to all resources of that type, no filtering needed (optimization)

**With `transitive` flag:**
- Wildcard authorization (`*`) → Authorization check remains **enabled**
- Rationale: Prevents the wildcard optimization from bypassing authorization checks in cross-resource scenarios

#### Search Query Processing

In `AbstractResourceAccessController.determineAuthorizationCheck()`:

```java
if (resourceAccess.wildcard() && !authorization.transitive()) {
    return AuthorizationCheck.disabled();
}
```

When a transitive authorization is combined with wildcard access, the system:
1. Keeps the authorization check enabled (doesn't take the optimization shortcut)
2. Still uses the process definition resource IDs to filter the query
3. Ensures proper authorization filtering even with wildcard permissions

### Example: Audit Log with Transitive Process Permissions

```java
public static final Authorization<AuditLogEntity>
    AUDIT_LOG_READ_PROCESS_INSTANCE_AUTHORIZATION =
        Authorization.of(a -> a.processDefinition().readProcessInstance().transitive());

public static final Authorization<AuditLogEntity>
    AUDIT_LOG_READ_USER_TASK_AUTHORIZATION =
        Authorization.of(a -> a.processDefinition().readUserTask().transitive());
```

These definitions mean:
- These authorizations use process definition resource IDs to filter audit log queries
- The resource type is `PROCESS_DEFINITION` but the authorization is applied when querying `AuditLogEntity`
- The transitive flag ensures that users with wildcard (`*`) process definition permissions still have their audit log queries filtered by process definition IDs
- **Without transitive:** Wildcard would disable the check, returning ALL audit logs
- **With transitive:** Wildcard keeps the check active, returning only audit logs for authorized process definitions

### Key Characteristics

- **Flag-Based**: Controlled by the boolean `transitive` field in `Authorization`
- **Wildcard Optimization Control**: Determines whether wildcard permissions disable authorization checks
- **Builder Support**: Enabled via `Builder.transitive()` method
- **Query Optimization**: Maintains filtering even with broad permissions

## Best Practices

### When to Use Conditional Authorizations

**Note:** Conditional authorizations are designed for post-filtering (get operations) where documents are available for condition evaluation.

✅ **Use conditional authorizations when:**
- You need to filter access during `getByKey` operations based on runtime resource properties
- Different types of the same resource have different access rules (checked after retrieval)
- You need null-safe authorization logic for individual resource access
- Implementing category-based or type-based access control for gets

❌ **Avoid conditional authorizations when:**
- You need filtering during search operations (conditions require documents, which aren't available during searches)
- Simple resource-based authorization is sufficient
- All instances of a resource type share the same authorization rules
- Conditions would always evaluate to true

**Behavior Note:** Search results may include documents that would be denied by conditional authorization in a subsequent get operation, since search filtering uses only resource IDs.

### When to Use Transitive Authorizations

✅ **Use transitive authorizations when:**
- Implementing cross-resource authorization patterns (e.g., using process definition permissions to filter audit logs)
- Users with wildcard permissions should still have queries filtered (not bypass all checks)
- You need to prevent the wildcard optimization from disabling authorization checks
- The authorization uses one resource type's IDs to filter queries on a different resource type

❌ **Avoid transitive authorizations when:**
- Authorizing the resource directly (e.g., process definition authorization for process definition queries)
- You want wildcard permissions to disable authorization checks (standard optimization)
- Resources are independent with no cross-resource authorization pattern

### Combining Multiple Authorizations

Use `AuthorizationConditions.anyOf()` when multiple authorization paths exist:

```java
AuthorizationCondition condition = AuthorizationConditions.anyOf(
    directAuthorization,
    transitiveAuthorization.withCondition(predicate),
    anotherConditionalAuthorization
);
```

This creates an OR relationship where any satisfied authorization grants access.

## Further Reading

- [Identity Documentation](identity.md) - User and role management
- [Testing Guidelines](testing.md) - Testing authorization features

