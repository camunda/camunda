---
name: Feature Request
about: Allow users to choose ancestor scope resolution strategy in batch move operations
title: 'Allow users to choose ancestor scope resolution strategy in batch move operations'
labels: kind/feature-request
---

**Component**: Zeebe

## User Story

Currently, batch move operations in the Orchestration Cluster API use a fixed ancestor scope resolution strategy (`inferAncestorScopeFromSourceHierarchy` set to `true`). However, there are scenarios where users might want to use the simpler "source parent" strategy instead.

In single process instance modification operations, users can choose between two ancestor scope resolution strategies:
1. **Inferred from source hierarchy** (`inferAncestorScopeFromSourceHierarchy`): Traverses the source element's hierarchy to determine the appropriate ancestor scope
2. **Source parent key** (`useSourceParentKeyAsAncestorScopeKey`): Directly uses the source element's parent key as the ancestor scope, which is simpler and was the original behavior before the "inferred" strategy was introduced

Before the migration to "inferred" as the default, batch move operations used the "source parent" strategy and it worked well. The question is whether batch move operations should support both strategies to give users flexibility based on their use case.

**Why this would be beneficial:**
- Provides users with flexibility to choose the most appropriate ancestor scope resolution strategy for their specific use case
- Maintains backward compatibility for users who prefer the original "source parent" behavior
- Allows users to optimize for simplicity (source parent) vs. more complex hierarchy traversal (inferred) based on their process structure

**Related documentation:**
- [Operate docs on batch modifications](https://docs.camunda.io/docs/next/components/operate/userguide/process-instance-batch-modification/#non-supported-modifications) mention that "Move modifications are currently not possible for elements with multiple running scopes"
- [API Specs](https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/modify-process-instances-batch-operation/) do not specify implementation details about ancestor scope resolution

## Design

Add an optional parameter to batch move operation instructions that allows users to specify which ancestor scope resolution strategy to use:
- Keep the current default behavior (`inferAncestorScopeFromSourceHierarchy: true`) for backward compatibility
- Allow users to optionally specify `useSourceParentKeyAsAncestorScopeKey: true` to use the source parent strategy instead
- Make these options mutually exclusive to avoid ambiguity

Example API enhancement:
```json
{
  "operationType": "MODIFY_PROCESS_INSTANCE",
  "processInstanceKeys": [...],
  "modification": {
    "moveInstructions": [
      {
        "sourceElementId": "taskA",
        "targetElementId": "taskB",
        "ancestorScopeResolutionStrategy": "SOURCE_PARENT" // or "INFERRED" (default)
      }
    ]
  }
}
```

Or keep the existing boolean flags approach:
```json
{
  "operationType": "MODIFY_PROCESS_INSTANCE",
  "processInstanceKeys": [...],
  "modification": {
    "moveInstructions": [
      {
        "sourceElementId": "taskA",
        "targetElementId": "taskB",
        "useSourceParentKeyAsAncestorScopeKey": true  // optional, defaults to false
      }
    ]
  }
}
```

## Technical requirements

1. Extend the batch move operation API to accept an ancestor scope resolution strategy parameter
2. Update the `RequestMapper.mapProcessInstanceModificationMoveBatchInstruction` method to map the strategy parameter to the appropriate internal instruction format
3. Ensure the engine correctly processes both strategies for batch operations
4. Add validation to prevent both strategies from being specified simultaneously
5. Update API documentation to explain both strategies and when to use each
6. Add tests for both strategies in batch operations

## Links

- Original PR that re-introduced `useSourceParentKeyAsAncestorScopeKey`: #43429
- Discussion thread: https://github.com/camunda/camunda/pull/43429#discussion_r2668126559
- Related code: `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/mapper/RequestMapper.java:1334`
