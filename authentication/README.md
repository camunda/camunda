# Camunda Authentication

Authentication and membership resolution for Camunda 8, including OIDC token processing and Spring Security integration.

## Overview

This module handles:
- **Token Processing** – Parsing and validating OIDC tokens
- **Membership Resolution** – Resolving groups, roles, and permissions from token claims or database
- **Spring Security Integration** – Authentication providers and filters
- **OIDC Configuration** – Managing OIDC provider configuration and claims mapping

### New Camunda Security Library

We will step by step migrate the existing authentication and authorization APIs to a new library.

Repo: https://github.com/camunda/camunda-security-library

Epic: https://github.com/camunda/camunda-security-library/issues/9

### 📌 Migration Note: Using Shared Security APIs

As of **Camunda 8.10.0+**, the core authentication and authorization APIs live in a separate library:

#### API Location: `camunda-security-library-api`

- `CamundaAuthentication` – Authentication context (user, roles, groups, claims)

### For Integrations

When using this authentication module, you'll need both:

```xml
<!-- Security API (defines contracts) -->
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-security-library-api</artifactId>
</dependency>

<!-- Authentication implementation -->
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-authentication</artifactId>
</dependency>
```

