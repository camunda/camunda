# Gateway Authorization Layer Documentation

> **Note:** This documentation covers the authorization layer used by Camunda's REST API gateway for resource access control. The **engine authorization checks** (used for command processing in the workflow engine) work differently and are not covered here. While currently focused on conditional and transitive authorizations, this document will expand to cover all gateway authorization and identity-related features in the future.

## Overview

Camunda's gateway authorization layer provides fine-grained access control for resources accessed through the REST API. This authorization system operates at the gateway level and supports standard resource-based permissions as well as advanced features like **conditional authorizations** and **transitive authorizations** that enable more sophisticated access control patterns.

**Important:** This authorization layer is distinct from the engine's authorization checks. The gateway authorization layer handles query filtering and resource access control for REST API operations, while the engine performs separate authorization checks for command processing (e.g., job completion, message correlation, process deployment).

## Authorization System Components

The authorization system consists of several key areas:

### Core Authorization Concepts

Understanding the fundamental building blocks of the authorization system:
- **Authorization Structure**: Resource types, permissions, resource IDs, and suppliers
- **Authorization Conditions**: Single and AnyOf authorization patterns
- **Pre-filtering vs Post-filtering**: How authorization works in search and get operations

📖 [Learn about Core Concepts →](authorizations/core-concepts.md)

### Advanced Authorization Features

#### Conditional Authorizations

Context-aware access control that applies authorization rules based on runtime conditions:
- Only evaluated during get operations (post-filtering)
- Enables category-based and type-specific access control
- Supports null-safe authorization patterns

📖 [Learn about Conditional Authorizations →](authorizations/conditional-authorizations.md)

#### Transitive Authorizations

Cross-resource authorization patterns that prevent wildcard optimization:
- Controls wildcard permission handling during searches
- Enables using one resource type's permissions to filter another
- Maintains query filtering even with broad permissions

📖 [Learn about Transitive Authorizations →](authorizations/transitive-authorizations.md)

### Best Practices

Guidance on when and how to use authorization features effectively:
- When to use conditional vs transitive authorizations
- Design patterns for common scenarios
- Performance considerations
- Common pitfalls to avoid

📖 [Read Best Practices →](authorizations/best-practices.md)

## Quick Start Examples

### Simple Resource Authorization

```java
// Direct authorization for process definitions
Authorization<ProcessDefinitionEntity> auth =
    Authorization.of(a -> a.processDefinition().read())
        .with(ProcessDefinitionEntity::processDefinitionId);
```

### Conditional Authorization

```java
// Authorization that only applies when process definition exists
Authorization<AuditLogEntity> auth =
    Authorization.of(a -> a.processDefinition().readProcessInstance())
        .with(AuditLogEntity::processDefinitionId)
        .withCondition(al -> al.processDefinitionId() != null);
```

### Transitive Authorization

```java
// Use process permissions to filter audit logs
Authorization<AuditLogEntity> auth =
    Authorization.of(a -> a.processDefinition().readProcessInstance().transitive())
        .with(AuditLogEntity::processDefinitionId);
```

### Multi-Path Authorization

```java
// Grant access through multiple authorization paths
AuthorizationCondition condition = AuthorizationConditions.anyOf(
    directAuthorization,
    transitiveAuthorization.withCondition(predicate)
);
```

## Related Documentation

- [Identity Documentation](identity.md) - User and role management
- [Testing Guidelines](testing.md) - Testing authorization features

