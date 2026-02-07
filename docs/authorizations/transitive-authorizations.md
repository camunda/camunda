# Transitive Authorizations

The transitive flag is a technical optimization flag that controls how wildcard permissions are handled during search operations.

## Overview

**What it actually does:**
- Controls whether wildcard (`*`) permissions disable authorization checks during searches
- Enables cross-resource authorization patterns where one resource type's permissions are used to authorize queries on a related resource type

**What it does NOT do:**
- Does NOT automatically grant access to related resources
- Does NOT cascade permissions from parent to child resources
- Does NOT change what resources a user can access (resource IDs still determine access)

## Use Cases

The transitive flag is used when:
1. **Cross-Resource Authorization Patterns**: You want to use process definition permissions to authorize queries on related resources (e.g., audit logs, incidents)
2. **Preventing Wildcard Short-Circuit**: Users with wildcard process definition permissions should still have their queries filtered based on those permissions
3. **Maintaining Query Filtering**: Even with broad permissions, authorization filtering should remain active

## Implementation

A transitive authorization is created using the `transitive()` builder method:

```java
Authorization<AuditLogEntity> transitiveAuth =
    Authorization.of(a -> a.processDefinition().readProcessInstance().transitive());
```

**Important:** This authorization still uses process definition resource IDs to filter audit logs. The transitive flag only prevents wildcard optimization from disabling the check.

## How Transitive Authorizations Work

### Wildcard Handling

The transitive flag changes how wildcard authorizations are processed:

**Without `transitive` flag:**
- Wildcard authorization (`*`) → Authorization check is **disabled**
- Rationale: User has access to all resources of that type, no filtering needed (optimization)

**With `transitive` flag:**
- Wildcard authorization (`*`) → Authorization check remains **enabled**
- Rationale: Prevents the wildcard optimization from bypassing authorization checks in cross-resource scenarios

### Search Query Processing

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

## Example: Audit Log with Transitive Process Permissions

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

## Key Characteristics

- **Flag-Based**: Controlled by the boolean `transitive` field in `Authorization`
- **Wildcard Optimization Control**: Determines whether wildcard permissions disable authorization checks
- **Builder Support**: Enabled via `Builder.transitive()` method
- **Query Optimization**: Maintains filtering even with broad permissions

## When to Use Transitive Authorizations

✅ **Use transitive authorizations when:**
- Implementing cross-resource authorization patterns (e.g., using process definition permissions to filter audit logs)
- Users with wildcard permissions should still have queries filtered (not bypass all checks)
- You need to prevent the wildcard optimization from disabling authorization checks
- The authorization uses one resource type's IDs to filter queries on a different resource type

❌ **Avoid transitive authorizations when:**
- Authorizing the resource directly (e.g., process definition authorization for process definition queries)
- You want wildcard permissions to disable authorization checks (standard optimization)
- Resources are independent with no cross-resource authorization pattern

## Related Topics

- [Core Concepts](core-concepts.md) - Understanding authorization structure
- [Conditional Authorizations](conditional-authorizations.md) - Context-aware access control
- [Best Practices](best-practices.md) - Guidelines for using authorization features
