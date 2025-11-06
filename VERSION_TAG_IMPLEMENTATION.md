# Version Tag Support for Process Instance Creation

## Summary

This PR implements support for creating process instances using a `versionTag` parameter in the REST API `/v2/process-instances` endpoint.

## Motivation

Users requested the ability to create process instances by specifying a version tag instead of a specific version number or process definition key. This allows for more flexible deployment strategies where processes can be tagged (e.g., "stable", "beta", "v1.0.0") and instances can be created using these tags.

## Changes

### API Changes

Added a new oneOf alternative to the ProcessInstanceCreationInstruction schema:

```yaml
ProcessInstanceCreationInstructionByVersionTag:
  type: object
  title: Process creation by version tag
  required:
    - processDefinitionId
    - versionTag
  properties:
    processDefinitionId: string
    versionTag: string
    variables: object
    # ... other common properties
```

### Implementation

The implementation spans 10 files across 6 layers:

1. **Protocol Layer** (2 files)
   - Added versionTag field to ProcessInstanceCreationRecord
   - Added getVersionTag() method to interface

2. **OpenAPI Specification** (1 file)
   - Added ProcessInstanceCreationInstructionByVersionTag schema
   - Added example demonstrating usage

3. **REST API Layer** (3 files)
   - Updated deserializer to handle versionTag and validate mutual exclusivity
   - Added request mapper method for versionTag variant
   - Added validation for versionTag requests

4. **Service Layer** (1 file)
   - Added versionTag parameter to ProcessInstanceCreateRequest

5. **Broker Layer** (2 files)
   - Added setVersionTag() to broker request classes

6. **Engine Layer** (1 file)
   - Added getProcessByVersionTag() method
   - Integrated with existing ProcessState::getProcessByProcessIdAndVersionTag

### Validation

The implementation includes validation to ensure:
- Only one of `processDefinitionKey`, `version`, or `versionTag` is specified
- `versionTag` is provided with `processDefinitionId`
- `versionTag` is non-empty when specified

### Example Usage

```bash
curl -X POST http://localhost:8080/v2/process-instances \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionId": "my-process",
    "versionTag": "v1.0.0",
    "variables": {}
  }'
```

## Testing

The implementation requires:
- Unit tests for deserializer validation
- Unit tests for process resolution by version tag
- Integration tests for end-to-end flow
- Manual testing with deployed processes

See [VERSION_TAG_TESTING_GUIDE.md](/tmp/VERSION_TAG_TESTING_GUIDE.md) for detailed testing instructions.

## Build Requirements

- **Java 21** is required to build the project
- The current CI environment has Java 17, which prevents completing the build
- Once built, the OpenAPI generator will create `ProcessInstanceCreationInstructionByVersionTag` class

## Backward Compatibility

This change is fully backward compatible:
- Existing `processDefinitionKey` creation works unchanged
- Existing `processDefinitionId` + `version` creation works unchanged
- New `versionTag` option is purely additive

## References

- Original issue: https://github.com/camunda/camunda/issues/XXXXX
- ProcessState interface showing existing getProcessByProcessIdAndVersionTag support
- Existing oneOf pattern in ProcessInstanceCreationInstruction
