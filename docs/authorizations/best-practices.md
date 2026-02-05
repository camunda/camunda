# Best Practices

This page provides guidance on when and how to use Camunda's authorization features effectively.

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

## Combining Multiple Authorizations

Use `AuthorizationConditions.anyOf()` when multiple authorization paths exist:

```java
AuthorizationCondition condition = AuthorizationConditions.anyOf(
    directAuthorization,
    transitiveAuthorization.withCondition(al -> al.processDefinitionId() != null),
    anotherConditionalAuthorization
);
```

This creates an OR relationship where any satisfied authorization grants access.

## Design Patterns

### Direct Resource Authorization

When authorizing access to a resource directly (e.g., process definitions), use a simple authorization:

```java
Authorization<ProcessDefinitionEntity> auth =
    Authorization.of(a -> a.processDefinition().read())
        .with(ProcessDefinitionEntity::processDefinitionId);
```

### Cross-Resource Authorization

When using one resource's permissions to authorize another resource, use transitive authorizations:

```java
// Use process definition permissions to filter audit logs
Authorization<AuditLogEntity> auth =
    Authorization.of(a -> a.processDefinition().readProcessInstance().transitive())
        .with(AuditLogEntity::processDefinitionId);
```

### Multi-Path Authorization

When multiple authorization paths should grant access, use `anyOf`:

```java
AuthorizationCondition auditLogAuth = AuthorizationConditions.anyOf(
    // Direct audit log permission
    Authorization.of(a -> a.auditLog().read())
        .with(al -> al.category().name()),
    
    // Or process definition permission
    Authorization.of(a -> a.processDefinition().readProcessInstance().transitive())
        .with(AuditLogEntity::processDefinitionId)
        .withCondition(al -> al.processDefinitionId() != null)
);
```

## Performance Considerations

### Pre-filtering Optimization

- Pre-filtering (search operations) uses only resource IDs for filtering
- This is efficient because it happens at the database/search engine level
- Conditions are not evaluated during pre-filtering by design

### Post-filtering Behavior

- Post-filtering (get operations) evaluates conditions after fetching the document
- This happens in application code after the document is retrieved
- Conditions should be lightweight predicates that execute quickly

### Wildcard Permissions

- Without `transitive` flag: Wildcard permissions disable authorization checks (optimization)
- With `transitive` flag: Wildcard permissions still trigger filtering (necessary for cross-resource patterns)
- Consider the performance implications when designing authorization rules

## Common Pitfalls

### Expecting Conditions to Filter Searches

❌ **Incorrect assumption:**
```java
// Expecting this condition to filter search results
Authorization.of(a -> a.processDefinition().read())
    .withCondition(pd -> pd.version() > 5);  // Only evaluated during gets, not searches
```

✅ **Correct approach:**
- Understand that conditions only work for get operations
- For search filtering, rely on resource IDs or implement filtering in your query logic

### Forgetting to Use Transitive Flag

❌ **Problem:**
```java
// Without transitive, wildcard permissions bypass filtering
Authorization<AuditLogEntity> auth =
    Authorization.of(a -> a.processDefinition().readProcessInstance())
        .with(AuditLogEntity::processDefinitionId);
// Users with wildcard process permissions will see ALL audit logs
```

✅ **Solution:**
```java
// With transitive, wildcard permissions still filter by resource IDs
Authorization<AuditLogEntity> auth =
    Authorization.of(a -> a.processDefinition().readProcessInstance().transitive())
        .with(AuditLogEntity::processDefinitionId);
// Users with wildcard process permissions see only audit logs for authorized processes
```

## Testing Authorization Rules

When testing authorization implementations:

1. **Test pre-filtering**: Verify search operations return only authorized resources
2. **Test post-filtering**: Verify get operations properly check authorization
3. **Test conditions**: Ensure conditions correctly filter access during gets
4. **Test wildcard permissions**: Verify behavior with and without transitive flag
5. **Test multi-path authorization**: Ensure all paths work correctly in `anyOf` scenarios

## Related Topics

- [Core Concepts](core-concepts.md) - Understanding authorization structure
- [Conditional Authorizations](conditional-authorizations.md) - Context-aware access control
- [Transitive Authorizations](transitive-authorizations.md) - Cross-resource authorization patterns
