# Element Instance Introspection Endpoint - Implementation Status

## Summary
This document tracks the implementation status of the new introspection endpoint for Zeebe Gateway REST API.

## Completed Work

### 1. OpenAPI Specification ✅
- **File**: `zeebe/gateway-protocol/src/main/proto/v2/introspection.yaml`
- **Status**: Complete
- **Changes**:
  - Defined GET `/v2/introspection/element-instance` endpoint
  - Query parameters: `processInstanceKey` (required), `elementId` (required), `elementInstanceKey` (optional)
  - Response schema: `ElementInstanceWaitReason` with `type` and `details`
  - Error schemas: `AmbiguousElementInstanceError` for multi-instance ambiguity
  - Job worker wait details: `JobWorkerWaitDetails` with `jobType`, `worker`, `lastSeenAt`
  - Extension points for future wait reasons: message, signal, timer, error, escalation, user-task

- **File**: `zeebe/gateway-protocol/src/main/proto/v2/rest-api.yaml`
- **Status**: Complete  
- **Changes**:
  - Added "Introspection" tag
  - Added path reference to introspection.yaml

### 2. Broker-Side Job Worker Heartbeat Tracking ✅
- **Status**: Complete
- **Changes Made**:
  
  a) **JobTypeHeartbeatValue.java** (NEW)
     - Path: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/instance/JobTypeHeartbeatValue.java`
     - Msgpack-serialized value class
     - Fields: `worker` (String), `lastSeenAt` (long timestamp millis)
  
  b) **ZbColumnFamilies.java** (MODIFIED)
     - Added `JOB_TYPE_HEARTBEAT(138, PARTITION_LOCAL)` column family
  
  c) **MutableJobState.java** (MODIFIED)
     - Added interface methods:
       - `void updateJobTypeHeartbeat(String jobType, String tenantId, String worker, long timestamp)`
       - `@Nullable JobTypeHeartbeatValue getJobTypeHeartbeat(String jobType, String tenantId)`
  
  d) **DbJobState.java** (MODIFIED)
     - Added heartbeat column family with composite key `[[tenant_id, type]]`
     - Implemented both interface methods
     - Uses `DbTenantAwareKey` with `PlacementType.SUFFIX` for consistency
     - Separate read/write instances to prevent data consistency issues
  
  e) **JobBatchActivateProcessor.java** (MODIFIED)
     - Calls `updateJobTypeHeartbeat()` on EVERY ActivateJobs request
     - Updates BEFORE collecting jobs
     - Uses `clock.millis()` for timestamp consistency

## Remaining Work

### 3. Protocol Model Generation ⏳
- **Status**: Blocked by build environment issues
- **Action Required**:
  1. Run: `./mvnw -pl gateways/gateway-model clean generate-sources`
  2. Verify generated models in `gateways/gateway-model/target/generated-sources/`
  3. Expected models:
     - `ElementInstanceWaitReason`
     - `JobWorkerWaitDetails`
     - `AmbiguousElementInstanceError`

### 4. Broker Command & Request Implementation 🚧
**Complexity**: HIGH - Requires significant new infrastructure

#### What's Needed:
1. **New Broker Command Type**
   - Add `INTROSPECT_ELEMENT_INSTANCE` to `ValueType` enum
   - Create `ElementInstanceIntrospectionRecord` class
   - Define protobuf schema in `zeebe/protocol/src/main/proto/`

2. **Engine Processor**
   - Path: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/introspection/`
   - Create `IntrospectionElementInstanceProcessor`
   - Query element instance state
   - Resolve wait reason based on element type and state
   - Return introspection response

3. **Wait Reason Resolution**
   - Implement resolvers for each wait type:
     - `JobWorkerWaitReasonResolver`: Query job state + heartbeat
     - `MessageWaitReasonResolver`: Query message subscriptions
     - `TimerWaitReasonResolver`: Query timer state
     - (Future: Signal, Error, Escalation, UserTask)

4. **Gateway Request**
   - Path: `zeebe/gateway/src/main/java/io/camunda/zeebe/gateway/impl/broker/request/`
   - Create `BrokerIntrospectElementInstanceRequest`
   - Handle partition routing based on process instance key
   - Map broker response to gateway response

5. **Register Processor**
   - Wire up in `EventProcessors.java`

### 5. Service Layer 🚧
**File**: `service/src/main/java/io/camunda/service/IntrospectionServices.java`

```java
public final class IntrospectionServices extends ApiServices<IntrospectionServices> {
  
  public CompletableFuture<ElementInstanceWaitReason> introspectElementInstance(
      long processInstanceKey,
      String elementId,
      @Nullable Long elementInstanceKey) {
    
    // 1. Build broker request
    // 2. Route to correct partition
    // 3. Handle response/errors
    // 4. Map to protocol model
  }
  
  // Handle ambiguity error
  // Handle not-found error
}
```

**Key Logic**:
- If `elementInstanceKey` is null, broker must resolve active instance
- If multiple match → return ambiguity error with list of keys
- If none match → return 404
- If validation fails → return 400

### 6. REST Controller 🚧
**File**: `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/IntrospectionController.java`

```java
@CamundaRestController
@RequestMapping("/v2/introspection")
public class IntrospectionController {
  
  private final IntrospectionServices introspectionServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  
  @CamundaGetMapping(path = "/element-instance")
  public ResponseEntity<Object> introspectElementInstance(
      @RequestParam Long processInstanceKey,
      @RequestParam String elementId,
      @RequestParam(required = false) Long elementInstanceKey) {
    try {
      // Validate inputs
      // Call service
      // Map response
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
```

**Error Mapping**:
- Ambiguity → 400 with `AmbiguousElementInstanceError`
- Not found → 404
- Invalid params → 400
- Authorization → 401/403

### 7. Tests 🚧

#### a) Broker Integration Test
**Path**: `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/introspection/`
**File**: `IntrospectionElementInstanceTest.java`

Test scenarios:
- Job worker heartbeat is updated on ActivateJobs
- Heartbeat is retrieved correctly per jobType + tenant
- Multiple tenants have separate heartbeats
- Wait reason resolution for job-backed service task

#### b) Service Layer Test
**Path**: `service/src/test/java/io/camunda/service/`
**File**: `IntrospectionServicesTest.java`

Test scenarios:
- Happy path: single active element instance
- Ambiguity error: multiple element instances
- Not found: no matching element instance
- Validation: elementInstanceKey doesn't match processInstanceKey/elementId

#### c) Controller Test
**Path**: `zeebe/gateway-rest/src/test/java/io/camunda/zeebe/gateway/rest/controller/`
**File**: `IntrospectionControllerTest.java`

Test scenarios:
- GET request with all params returns 200
- GET request without elementInstanceKey resolves correctly
- Ambiguity returns 400 with elementInstanceKey list
- Not found returns 404
- Missing required params returns 400

#### d) End-to-End Test
**Path**: `zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/gateway/`
**File**: `IntrospectionElementInstanceIT.java`

Test flow:
1. Deploy process with service task
2. Start process instance
3. Worker polls for jobs (triggers heartbeat)
4. Call introspection endpoint
5. Verify response contains jobType, worker, lastSeenAt
6. Complete job
7. Verify introspection shows completion (or next wait state)

### 8. Documentation 🚧

#### a) API Documentation
- Update REST API docs with new endpoint
- Add examples for each wait reason type
- Document error responses

#### b) Developer Guide
- How to add new wait reason types
- Extension points for custom wait reasons
- Troubleshooting guide

## Technical Debt & Future Work

### Immediate Follow-ups:
1. Implement remaining wait reason types (message, timer, signal)
2. Add metrics for introspection endpoint usage
3. Performance testing for high-volume introspection queries
4. Caching strategy for frequently-queried element instances

### Future Enhancements:
1. Batch introspection (multiple element instances in one request)
2. Historical introspection (why did this element wait in the past)
3. Subscription-based introspection (notify when wait reason changes)
4. Integration with Operate UI for visual wait reason display

## Build Commands

### Generate Models:
```bash
./mvnw -pl gateways/gateway-model clean generate-sources -DskipTests -DskipChecks
```

### Build Gateway REST:
```bash
./mvnw -pl zeebe/gateway-rest clean install -DskipTests
```

### Build Engine:
```bash
./mvnw -pl zeebe/engine clean install -DskipTests
```

### Run Specific Test:
```bash
./mvnw -pl zeebe/engine test -Dtest=IntrospectionElementInstanceTest
```

### Full Build & Test:
```bash
./mvnw clean verify
```

## Estimated Effort

| Component | Effort | Priority |
|-----------|--------|----------|
| Protocol Models | 1 hour | P0 |
| Broker Command Infrastructure | 8 hours | P0 |
| Wait Reason Resolvers (Job Worker) | 4 hours | P0 |
| Service Layer | 3 hours | P0 |
| REST Controller | 2 hours | P0 |
| Tests | 6 hours | P0 |
| Wait Reason Resolvers (Other types) | 12 hours | P1 |
| Documentation | 4 hours | P1 |

**Total MVP (Job Worker only)**: ~24 hours  
**Total with all wait types**: ~36 hours

## Security Considerations

- ✅ No secrets in heartbeat state
- ✅ Tenant isolation in heartbeat storage
- ✅ Authorization checks in service layer
- ⏳ Rate limiting for introspection endpoint (TBD)
- ⏳ Audit logging for introspection calls (TBD)

## Performance Considerations

- Heartbeat updates are lightweight (single key-value write)
- Heartbeat queries are partition-local (no cross-partition overhead)
- Introspection routing uses process instance key → partition mapping
- Consider caching frequently-accessed introspection results
- Monitor introspection endpoint latency and throughput

## References

- [Original Issue/PR](TBD)
- [API Spec](zeebe/gateway-protocol/src/main/proto/v2/introspection.yaml)
- [Zeebe Engine State Management](zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/)
- [Gateway REST Controllers](zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/)
