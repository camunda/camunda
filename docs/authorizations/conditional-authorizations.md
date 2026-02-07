# Conditional Authorizations

Conditional authorizations allow you to define authorization rules that only apply when certain runtime conditions are met. This enables context-aware access control where the applicability of an authorization depends on the properties of the resource being accessed.

## Overview

**⚠️ Implementation Note:**
- ✅ **Conditions are evaluated in post-filtering** (get operations) where documents are available
- ℹ️ **Conditions are not evaluated in pre-filtering** (search operations) - only resource IDs are used for query filtering
- **By design:** Conditions require actual documents to evaluate, which are only available during get operations

## Use Cases

Conditional authorizations are particularly useful when:
1. **Resource Type Differentiation**: Different authorization rules should apply based on resource characteristics
2. **Category-Based Access**: Access should be granted only for specific categories or types within a resource
3. **Null-Safe Authorization**: Authorization should only apply when certain fields are present

## Implementation

A conditional authorization is created using the `withCondition()` method:

```java
Authorization<AuditLogEntity> authorization =
    Authorization.of(a -> a.processDefinition().readProcessInstance())
        .with(AuditLogEntity::processDefinitionId)
        .withCondition(al -> al.processDefinitionId() != null);
```

## How Conditional Authorizations Work

### Condition Field Usage

The `condition` field is **only evaluated during post-filtering** (get operations). During pre-filtering (search operations), conditions are intentionally not used.

### Pre-Filtering (Search Operations)

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

### Post-Filtering (Get Operations)

**Fully Implemented:** Conditions are evaluated during get operations to determine which authorizations apply.

**Process:**

1. The resource is fetched without authorization checks
2. For each authorization, the controller calls `appliesTo(document)` to test if the condition passes
3. Only authorizations where `appliesTo(document)` returns `true` are evaluated for access control
4. If no authorizations apply in a **single authorization** context, access is denied with a "not applicable" error
5. In an **AnyOf** context, at least one applicable authorization must grant access

## Example: Audit Log Authorization

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

## Key Methods

- **`Authorization.withCondition(Predicate<T> condition)`**: Attaches a condition to an authorization
- **`Authorization.appliesTo(T document)`**: Checks if the authorization's condition allows it to apply to a document
- **`AnyOfAuthorizationCondition.applicableAuthorizations(T document)`**: Filters authorizations to only those that apply to the given document

## When to Use Conditional Authorizations

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

## Related Topics

- [Core Concepts](core-concepts.md) - Understanding authorization structure
- [Transitive Authorizations](transitive-authorizations.md) - Cross-resource authorization patterns
- [Best Practices](best-practices.md) - Guidelines for using authorization features
