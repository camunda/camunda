# ADR-0002: Use Liquibase for Database Schema Management

## Status

Accepted

## Context

We need a reliable way to manage database schema changes across different environments. The chosen
tool should support versioning, be database-independent, and allow for declarative schema
management.

## Decision

We will use **Liquibase** to manage the database schema.

## Rationale

- **Database Independence**: Liquibase works with multiple database systems, ensuring flexibility
  for future changes.
- **Version Control for Schema**: It provides a structured way to track and apply database changes,
  ensuring consistency across environments.
- **Declarative Approach**: Changesets can be defined in XML, YAML, JSON, or SQL, making it easier
  to manage and review schema modifications.
- **Automation & Rollback Support**: Liquibase integrates well with CI/CD pipelines and provides
  rollback capabilities to revert changes when needed.

## Consequences

### Positive

- Simplifies database schema management across environments.
- Reduces manual intervention, minimizing the risk of errors.
- Supports automated deployments and rollbacks, improving DevOps workflows.

### Negative

- Requires learning Liquibase syntax and best practices.
- Initial setup and migration from existing schema management processes may take some effort.

## Alternatives Considered

- **Flyway**: Another popular database migration tool but follows a SQL-based approach rather than
  a declarative one.
- **Manual SQL Scripts**: Less structured and harder to track changes across environments.
