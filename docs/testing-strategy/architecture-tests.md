# Architecture Tests

> Back to [Testing Strategy](./README.md)

## Definition

Architecture tests (ArchUnit) verify structural properties of the codebase: dependency directions, naming conventions, annotation usage, and module boundaries.

## Rules

1. All ArchUnit tests live in `qa/archunit-tests/` — not scattered across modules
2. All ArchUnit test classes must be named `*ArchTest.java`
3. ArchUnit tests run on every PR as part of the `archunit-tests` CI job
4. New architectural constraints should be proposed via PR and reviewed by the team

## Current Coverage

The following architectural properties are enforced (see `qa/archunit-tests/`):

- Processor naming conventions
- `@VisibleForTesting` usage (only accessed from test code)
- Client dependency isolation (no protocol dependency)
- Controller authorization patterns
- Engine class dependency rules
- Protocol immutability
- REST controller annotations
- State modification rules
- Migration task registration
