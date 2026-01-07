---
name: Feature Request
about: Allow users to choose ancestor scope resolution strategy in batch move operations
title: 'Allow users to choose ancestor scope resolution strategy in batch move operations'
labels: kind/feature-request
---

**Component**: Zeebe

## User Story

Currently, batch move operations in the Orchestration Cluster API use a fixed ancestor scope resolution strategy (`inferAncestorScopeFromSourceHierarchy` set to `true`). However, this is more costly than necessary for the common case and differs from the original design.

In single process instance modification operations, users can choose between two ancestor scope resolution strategies:
1. **Inferred from source hierarchy** (`inferAncestorScopeFromSourceHierarchy`): Traverses the source element's hierarchy to determine the appropriate ancestor scope - useful when source and target elements are not siblings
2. **Source parent key** (`useSourceParentKeyAsAncestorScopeKey`): Directly uses the source element's parent key as the ancestor scope, which is simpler, more performant (no hierarchy traversal), and was the original behavior before the "inferred" strategy was introduced

Before the migration to "inferred" as the default, batch move operations used the "source parent" strategy and it worked well for the majority of use cases where source and target elements are siblings within the same direct scope.

**Why this would be beneficial:**
- Provides users with flexibility to choose the most appropriate ancestor scope resolution strategy for their specific use case
- Defaults to the more performant `sourceParent` strategy which avoids unnecessary process model hierarchy traversal
- Aligns with the original design and works well for common cases where elements are siblings
- Allows users to explicitly opt into `inferred` strategy when they know source and target elements are not siblings (do not belong to the same direct scope)

**Related documentation:**
- [Operate docs on batch modifications](https://docs.camunda.io/docs/next/components/operate/userguide/process-instance-batch-modification/#non-supported-modifications) mention that "Move modifications are currently not possible for elements with multiple running scopes"
- [API Specs](https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/modify-process-instances-batch-operation/) do not specify implementation details about ancestor scope resolution

## Design

Add an optional parameter to batch move operation instructions that allows users to specify which ancestor scope resolution strategy to use. This should follow the same pattern as single process instance modifications, using the `AncestorScopeInstruction` schema defined in the REST API (see `zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml`).

For batch operations, only allow `sourceParent` or `inferred` ancestor scope types (not `direct`, which requires specifying a specific element instance key that doesn't make sense in the context of batch operations across multiple process instances).

**Recommended API design** (consistent with single instance modifications):
```json
{
  "operationType": "MODIFY_PROCESS_INSTANCE",
  "processInstanceKeys": [...],
  "modification": {
    "moveInstructions": [
      {
        "sourceElementId": "taskA",
        "targetElementId": "taskB",
        "ancestorScopeInstruction": {
          "ancestorScopeType": "sourceParent"  // default, or "inferred" when needed
        }
      }
    ]
  }
}
```

**Key design decisions:**
- Use the existing `AncestorScopeInstruction` polymorphic schema pattern for consistency
- Default to `sourceParent` behavior as it's less costly (no process model hierarchy traversal), aligns with the original design before the inferring feature was introduced, and works well for the common case where source and target elements are siblings in the same direct scope
- Only support `sourceParent` and `inferred` types (exclude `direct` as it's not applicable to batch operations)
- The `ancestorScopeInstruction` field should be optional; when omitted, default to `sourceParent`
- `inferred` should be explicitly specified when users know that the modification source and target elements are not siblings (they do not belong to the same direct scope)

## Technical requirements

1. Extend the batch move operation schema to accept the `ancestorScopeInstruction` parameter (using the existing `AncestorScopeInstruction` polymorphic schema)
2. Add validation to ensure only `sourceParent` and `inferred` ancestor scope types are allowed in batch operations (reject `direct` type)
3. Update the `RequestMapper.mapProcessInstanceModificationMoveBatchInstruction` method to map the `ancestorScopeInstruction` parameter to the appropriate internal instruction format
4. Ensure the engine correctly processes both `sourceParent` and `inferred` strategies for batch operations
5. Default to `sourceParent` behavior when `ancestorScopeInstruction` is not specified (for performance and alignment with original design)
6. Update API documentation to explain both strategies, when to use each (particularly when `inferred` is needed for non-sibling elements), and why `direct` is not supported for batch operations
7. Add tests for both `sourceParent` (default) and `inferred` strategies in batch operations
8. Add validation tests to ensure `direct` type is properly rejected for batch operations

## Links

- Original PR that re-introduced `useSourceParentKeyAsAncestorScopeKey`: #43429
- Discussion thread: https://github.com/camunda/camunda/pull/43429#discussion_r2668126559
- Related code: `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/mapper/RequestMapper.java:1334`
