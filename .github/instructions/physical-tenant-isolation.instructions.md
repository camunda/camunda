---
applyTo: "authentication/src/main/**,configuration/src/main/java/io/camunda/configuration/physicaltenants/**,dist/src/main/java/io/camunda/application/commons/pt/**,dist/src/main/java/io/camunda/application/commons/security/**,zeebe/gateway-rest/src/main/**/PhysicalTenant*,zeebe/gateway-grpc/src/main/**/interceptors/**,gateways/gateway-mcp/src/main/**,docs/adr/orchestration-cluster/**,docs/monorepo-docs/physical-tenant-isolation-tests.md"
---

# Physical tenant isolation — review guidance

A change in these paths can change what a physical tenant accepts, sees or changes of another
tenant: tokens of providers that it does not assign, sessions and cookies, authorizations, or data.

When a pull request adds or changes such an isolation boundary, check that it has a rejection test
on each path where a request reaches a tenant:

- REST (`/physical-tenants/<id>/v2/...`)
- gRPC through the embedded gateway
- gRPC through the standalone gateway
- browser login and web app session
- the MCP gateway, where it applies

A rejection test sends the forbidden request and asserts the rejection, for example `401`, `403`,
or gRPC `UNAUTHENTICATED`. Where it makes sense, it also sends the allowed request as a control. A
positive test alone does not count, and a test that builds the filter chains directly does not
prove the wiring of the application: prefer an end-to-end test in `qa/acceptance-tests`.

If a path has no rejection test, ask for one, or for a reason why the path does not apply. The full
rule, which the Identity team uses for every new isolation criterion, is in
[`docs/monorepo-docs/physical-tenant-isolation-tests.md`](../../docs/monorepo-docs/physical-tenant-isolation-tests.md).
