# ADR-0001: Use MyBatis as the ORM Framework for the RDBMS Module

## Status

Accepted

## Context

We need an Object-Relational Mapping (ORM) framework for the RDBMS module to facilitate database
interactions. The chosen framework should be simple, lightweight, and allow for efficient mapping
between Java objects and SQL statements.

## Decision

We will use **MyBatis** as the ORM framework for the RDBMS module.

## Rationale

- **Simplicity & Lightweight**: MyBatis is a minimalistic framework that avoids the complexity of
  full-fledged ORM solutions like Hibernate.
- **SQL Control**: It allows for direct SQL execution with fine-grained control over queries, which
  can be beneficial for performance tuning and complex queries.
- **Proven Experience**: MyBatis was successfully used in **Camunda 7**, meaning we already have
  significant experience with it, reducing the learning curve and implementation risks.
- **Flexibility**: It supports dynamic SQL generation and allows for mapping Java objects to SQL
  statements without requiring extensive annotations or configurations.

## Consequences

### Positive

- Faster onboarding due to existing team expertise.
- More control over SQL queries, leading to optimized performance.
- Avoids the overhead and potential pitfalls of more complex ORM frameworks.

### Negative

- Requires manual handling of some database operations that full ORM solutions might automate.
- May require additional effort to manage complex object relationships compared to fully automated
  ORM tools.

## Alternatives Considered

- **Hibernate**: A more comprehensive ORM solution but introduces additional complexity and
  potential performance overhead.
- **JPA (Jakarta Persistence API)**: Standardized ORM approach but would require additional
  abstraction layers and potentially lead to less control over SQL execution.
