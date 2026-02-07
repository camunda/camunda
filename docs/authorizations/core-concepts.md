# Core Concepts

This page covers the fundamental concepts of Camunda's authorization system.

## Authorization Structure

An `Authorization` consists of:
- **Resource Type**: The type of resource being protected (e.g., `PROCESS_DEFINITION`, `USER_TASK`, `AUDIT_LOG`)
- **Permission Type**: The operation being authorized (e.g., `READ`, `CREATE_PROCESS_INSTANCE`, `UPDATE_USER_TASK`)
- **Resource IDs**: List of resource IDs the user has permission to access (populated from user's permissions during pre-filtering, used to filter queries)
- **Resource ID Supplier**: Function to extract the resource ID from a document during post-filtering (used to compare against user's permitted resource IDs)
- **Condition**: Optional predicate to determine if authorization applies to a specific document (**only evaluated in post-filtering**)
- **Transitive Flag**: Controls whether wildcard permissions disable authorization checks (for cross-resource authorization patterns)

## Authorization Conditions

The `AuthorizationCondition` interface allows expressing how multiple authorizations should be evaluated:

- **Single Authorization**: Wraps a single authorization requirement
- **AnyOf Authorization**: Disjunctive condition where access is granted if any of the child authorizations are satisfied

## Resource IDs vs Resource ID Supplier

These two fields serve distinct purposes in different phases of authorization:

### Resource IDs (`resourceIds`)

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

### Resource ID Supplier (`resourceIdSupplier`)

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

### Why Both Are Needed

**Pre-filtering (Search):** We know the user's permissions but don't have documents yet
- Use `resourceIds` (user's permitted IDs) to filter the query

**Post-filtering (Get):** We have the document but need to extract which resource ID it belongs to
- Use `resourceIdSupplier` to extract the ID from the document, then verify permission

### Practical Example: Audit Log Authorization

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

## Related Topics

- [Conditional Authorizations](conditional-authorizations.md) - Context-aware access control
- [Transitive Authorizations](transitive-authorizations.md) - Cross-resource authorization patterns
- [Best Practices](best-practices.md) - Guidelines for using authorization features
