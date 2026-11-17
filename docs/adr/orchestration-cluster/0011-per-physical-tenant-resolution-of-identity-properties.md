# ADR-0011: Per-Physical-Tenant Resolution of Identity Properties

**DRI**: Timothy Cline ([@timcline](https://github.com/timcline))

**Deciders**: Timothy Cline ([@timcline](https://github.com/timcline))

**Status**: Accepted (8.10)

**Purpose**: Defines, for each identity property, which value a physical tenant and each of its OIDC providers use.

**Audience**: Engineers who work on physical-tenant authentication, and the authors of its configuration reference.

## Context

The identity properties of a physical tenant resolve in two layers. In the tenant layer, a value under `camunda.physical-tenants.<id>.security.*` overrides the root value ([ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md)). In the provider layer, a named provider (`providers.oidc.<id>`) can use the value of the default slot (`oidc.*`) when it sets none. The security library decides this layer for the principal claims ([CSL ADR-0034](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0034-named-providers-inherit-principal-claims.md)) and the groups claim (CSL ADR-0040).

No record gave the rule of each property in both layers, so operators and reviewers had to read the code. The startup checks also compared issuers more strictly than the runtime, and the operator docs said that a tenant cannot define its own providers, which the code allows.

## Decision

**D1. The tenant layer applies first, then the provider layer.**
The tenant's configuration is resolved from the root and the tenant overlay. Then each named provider of that configuration takes the missing values that it inherits from the tenant's default slot.

**D2. A named provider inherits only its claims.**
A named provider without its own principal claims or groups claim uses the claims of the default slot. It inherits no other value: issuer, client ID and secret, client authentication, endpoints, keys, audiences, scopes, and timing are its own, or come from discovery, or use the default.

**D3. A tenant can override every provider value, field by field.**
A value that the tenant sets wins over the root value, and a value that it omits keeps the root value. A list, such as audiences or scopes, replaces the root list. The authentication method and the other properties of the deny-list ([ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md) D7) stay cluster-wide. The authorizations switch uses the tenant value, else the root value.

**D4. A tenant can define providers of its own.**
A provider that exists only in a tenant is valid, as is a root provider. Like every provider of a non-default tenant, it must be in the tenant's provider selection ([ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md)).

**D5. Startup checks compare issuers as the runtime does.**
Two issuer URIs that differ only by a trailing slash are one issuer, because discovery settles both on the provider's issuer. Thus two providers of one tenant must not use issuers that differ only in this way. A tenant that inherits the root client secret must not change the issuer or the token endpoint of that provider. For the issuer, a trailing slash is not a change. The token endpoint is called as configured, so any change to it counts. Only these two values count, because the client secret goes only to the token endpoint.

## Consequences

- The configuration reference can give the rule of each property in both layers from this record.
- A configuration with two providers whose issuers differ only by a trailing slash now fails at startup. Before, it started and the runtime used only the first provider.
- A tenant that only adds or removes a trailing slash on an inherited issuer no longer needs its own client secret. A trailing slash on the token endpoint still requires one.
- A new provider value does not inherit from the default slot unless a new decision says so.

## Source

- [#68211](https://github.com/camunda/camunda/issues/68211): write down the per-tenant resolution rule for every identity property
- [camunda-security-library#750](https://github.com/camunda/camunda-security-library/pull/750): CSL ADR-0040, groups modes from every OIDC provider
- [#61896](https://github.com/camunda/camunda/issues/61896): issuer URI with or without a trailing slash

