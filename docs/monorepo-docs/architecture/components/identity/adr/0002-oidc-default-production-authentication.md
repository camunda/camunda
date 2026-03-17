# ADR-0002: OIDC as Default Production Authentication

## Status

Accepted

## Context

Basic authentication is simple to configure but does not provide multi-factor authentication (MFA),
single sign-on (SSO), centralized account lockout, or enterprise password policies. These
capabilities are important for production and enterprise deployments of Camunda 8.

## Decision

Recommend OIDC as the default authentication method for production deployments (both SaaS and
Self-Managed). Basic authentication remains supported for simple Self-Managed setups and local
development scenarios. An optional no-auth mode is also available for local or demo scenarios.

## Consequences

### Positive

- Better security and user experience through SSO, MFA, and centralized user lifecycle management
  via enterprise IdPs.
- Alignment with enterprise security standards and compliance requirements.
- Support for machine-to-machine access via the OAuth2 client credentials grant.

### Negative

- Requires customers to operate or adopt an OIDC-capable IdP (e.g. Keycloak, Okta, Auth0,
  Microsoft Entra ID, Amazon Cognito).
- Additional configuration complexity compared to Basic auth.

