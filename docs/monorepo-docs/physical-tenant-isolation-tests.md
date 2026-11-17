# Rejection tests for physical tenant isolation

The Identity team uses this rule for every new physical tenant isolation criterion. Reviewers ask
for the missing paths when a criterion does not follow it.

Every acceptance criterion that isolates physical tenants links to a test that proves the rejection
on each path. A positive test alone does not prove isolation: a path that accepts everything passes
it.

**Isolation criterion:** a criterion that says a tenant must not accept, see or change something of
another tenant. For example: a token of a provider that the tenant does not assign
([ADR-0004](https://github.com/camunda/camunda/blob/main/docs/adr/orchestration-cluster/0004-per-physical-tenant-provider-selection-via-assigned.md)), the session or cookie of
another tenant, or the authorizations or data of another tenant.

**Paths:** the entry points where a request reaches a tenant.

|           Path            |                      Example of a rejection                       |
|---------------------------|-------------------------------------------------------------------|
| REST                      | `401` or `403` on `/physical-tenants/<id>/v2/...`                 |
| gRPC, embedded gateway    | `UNAUTHENTICATED` or `PERMISSION_DENIED`                          |
| gRPC, standalone gateway  | `UNAUTHENTICATED` or `PERMISSION_DENIED`                          |
| Browser login and session | the login is refused, or another tenant's session gives no access |
| MCP gateway               | `401` or `403` on the tenant's MCP endpoint, where it applies     |

**Rejection test:**

- The test sends the forbidden request and asserts the rejection. Prefer an end-to-end test in
  `qa/acceptance-tests`: a test that builds the filter chains directly does not prove the wiring
  of the application.
- Where it makes sense, the same test also sends the allowed request as a control, so that a
  broken setup cannot cause the rejection.

**In the acceptance criterion:** name each path and link its test. If a path does not apply, say
why instead of leaving it out. For example:

```markdown
- [ ] A tenant rejects a token of a provider that it does not assign.
  - REST, gRPC (embedded): `PhysicalTenantOidcAssignedProvidersIT#shouldRejectTokenOfProviderNotAssignedToTenant`
  - gRPC (standalone gateway): `PhysicalTenantGrpcOidcAssignedProvidersStandaloneGatewayIT`
  - Browser login: <test, or why it does not apply>
  - MCP gateway: <test, or why it does not apply>
```
