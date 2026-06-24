# Camunda Security

Security core, services, protocol, and validation for Camunda 8's authentication and authorization system.

## Module Structure

- **`security-core/`** – Core security implementations (authorization checker, authentication service strategies)
- **`security-services/`** – Business logic services for authorization and authentication workflows
- **`security-protocol/`** – Protocol-level security definitions and enums (permission types, resource types, owner types)
- **`security-validation/`** – Validation rules and checkers for security-related inputs

### New Camunda Security Library

We will step by step migrate the existing authentication and authorization APIs to a new library.

Repo: https://github.com/camunda/camunda-security-library

Epic: https://github.com/camunda/camunda-security-library/issues/9

### 📌 Migration Note: Shared Security Library

As of **Camunda 8.10.0+**, core security API classes have been extracted to a shared library for better reusability:

### For Module Consumers

If you're depending on security APIs, ensure you have the API library in your dependencies:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-security-library-api</artifactId>
</dependency>
```

