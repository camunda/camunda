# Reference Examples

> Back to [Testing Strategy](../README.md)

## Unit Test References

| Quality | File | Why |
|---------|------|-----|
| Gold | `zeebe/engine/.../BatchOperationPageProcessorTest.java` | Given/when/then, single behavior, clean mocking |
| Gold | `operate/webapp/.../ResolveIncidentHandlerTest.java` | `@ExtendWith(MockitoExtension.class)`, `InOrder` verification |
| Gold | `tasklist/webapp/.../TaskServiceTest.java` | `@ParameterizedTest`, error path testing |

## Integration Test References

| Quality | File | Why |
|---------|------|-----|
| Gold | `zeebe/exporters/.../ElasticsearchExporterIT.java` | Singleton container, `TestSearchContainers`, smart cleanup |
| Gold | `dist/.../AbstractCamundaDockerIT.java` | Network-aware multi-container, deterministic cleanup |
| Gold | `operate/qa/.../OperateSearchAbstractIT.java` | Lifecycle hooks, cache clearing, mock auth |

## Contract Test References

| Quality | File / Concept | Why |
|---------|---------------|-----|
| Target | Java Client Pact consumer tests (to be created) | Consumer-driven, verifies client assumptions against v2 REST API |
| Target | Tasklist Frontend Pact consumer tests (to be created) | Leverages existing Zod schemas, verifies FE expectations |
| Target | Exporter message Pact (to be created) | Verifies ES/OS index schema contract between exporter and consumers |
| Keep | Spectral OpenAPI linting (existing) | Spec validity — complementary to Pact |
| Keep | Buf protobuf backward compat (existing) | gRPC schema contract — already enforced |
| Reclassify | `zeebe/gateway-rest/.../JobControllerTest.java` | **API slice test** (integration), not a contract test |
| Reclassify | `qa/c8-orchestration-cluster-e2e-test-suite/.../process-instance-get-api.spec.ts` | **E2E API validation** — too slow for PR feedback, keep as nightly |

## E2E Test References

| Quality | File | Why |
|---------|------|-----|
| Gold | `operate/client/e2e-playwright/tests/login.spec.ts` | No `waitForTimeout`, Page Object Model, accessible selectors |
| Gold | `operate/client/e2e-playwright/tests/processInstance.spec.ts` | `expect.poll()`, `test.slow()`, fixture architecture |
| Avoid | `operate/client/e2e-playwright/visual/processInstance.spec.ts` | 6x `waitForTimeout(500)` — anti-pattern |

## Test Utility References

| Utility | File | Purpose |
|---------|------|---------|
| Container factory | `zeebe/test-util/.../TestSearchContainers.java` | Single source of truth for container images |
| Engine test rule | `zeebe/engine/.../EngineRule.java` | Full engine bootstrap for engine tests |
| State extension | `zeebe/engine/.../ProcessingStateExtension.java` | Inject ZeebeDb state per test |
| Recording exporter | `zeebe/test-util/.../RecordingExporterTestWatcher.java` | Record/replay events, dump on failure |
| REST test base | `zeebe/gateway-rest/.../RestControllerTest.java` | `WebTestClient` + auth stubs |
| Operate IT base | `operate/qa/.../OperateSearchAbstractIT.java` | Container lifecycle + auth mocking |
| Playwright fixtures | `operate/client/e2e-playwright/e2e-fixtures.ts` | Page objects + auth session reuse |
