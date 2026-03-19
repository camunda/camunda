# engineer-stream-19 Summary

**Timestamp:** 2026-03-19T10:26:57.493Z

All 5 tests pass.

## Changes
- `event-bridge/event-bridge-gateway/src/main/java/io/camunda/eventbridge/gateway/controller/GlobalExceptionHandler.java`: Added `HttpRequestMethodNotSupportedException` handler → 405 `METHOD_NOT_ALLOWED`; imported `HttpRequestMethodNotSupportedException`
- `event-bridge/event-bridge-gateway/pom.xml`: Added `json-path` test dependency (required by `MockMvcResultMatchers.jsonPath`)
- `event-bridge/event-bridge-gateway/src/test/java/io/camunda/eventbridge/gateway/controller/GlobalExceptionHandlerTest.java`: New test class — 5 `@Nested` tests covering all 5 exception handlers via `MockMvcBuilders.standaloneSetup`

## Verification
- Build: ✅
- Tests: ✅ 5/5 pass

## Notes
The handler already covered missing params, type mismatches, unreadable bodies, and the catch-all. The `HttpRequestMethodNotSupportedException` handler was the meaningful gap: without it, a wrong-method request (e.g. `POST` to a `GET`-only endpoint) fell through to the `Exception` catch-all and incorrectly returned `500` instead of `405`. Tests use `MockMvcBuilders.standaloneSetup` with a minimal inner `TestController` to trigger each Spring MVC exception without requiring a full application context.
