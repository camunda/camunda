# HandlesIntent Annotation TODO List

This document lists all handlers that need to be annotated with `@HandlesIntent`.

## Scope

- **zeebe/engine**: Processors and Event Appliers
- **zeebe/exporters**: Export Handlers (camunda-exporter and rdbms-exporter)

## Important Notes

- **Processors**: Add annotations above the class declaration. Use the intent class and type as shown in the registration files.
- **Event Appliers**: Add annotations above the class declaration. If a version is specified (other than 1), include `version` attribute.
- **Export Handlers**: Add annotations for all intents that the handler checks in its `handlesRecord()` or `canExport()` method. Some handlers may use negative checks (e.g., handle all EXCEPT certain intents) - these need manual review.
- **BpmnStreamProcessor**: This is a special case that handles multiple ProcessInstanceIntent values dynamically. It needs multiple `@HandlesIntent` annotations.

## Examples

### Single Intent (Processor)

```java
@HandlesIntent(intent = UserIntent.class, type = "CREATE")
public class UserCreateProcessor implements DistributedTypedRecordProcessor<UserRecord> {
  // ...
}
```

### Single Intent with Version (Event Applier)

```java
@HandlesIntent(intent = UserTaskIntent.class, type = "CREATED", version = 2)
public class UserTaskCreatedV2Applier implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {
  // ...
}
```

### Multiple Intents (Export Handler or Multi-Intent Processor)

```java
@HandlesIntents({
  @HandlesIntent(intent = ProcessInstanceIntent.class, type = "ACTIVATE_ELEMENT"),
  @HandlesIntent(intent = ProcessInstanceIntent.class, type = "COMPLETE_ELEMENT"),
  @HandlesIntent(intent = ProcessInstanceIntent.class, type = "TERMINATE_ELEMENT"),
  @HandlesIntent(intent = ProcessInstanceIntent.class, type = "COMPLETE_EXECUTION_LISTENER"),
  @HandlesIntent(intent = ProcessInstanceIntent.class, type = "CONTINUE_TERMINATING_ELEMENT")
})
public class BpmnStreamProcessor implements TypedRecordProcessor<UnifiedRecordValue> {
  // ...
}
```

## 1. Processors

Processors in the `zeebe/engine` module that implement `TypedRecordProcessor` interface.

- [x] **AdHocSubProcessInstructionActivateProcessor**
  - Intent: `AdHocSubProcessInstructionIntent.ACTIVATE`
- [x] **AdHocSubProcessInstructionCompleteProcessor**
  - Intent: `AdHocSubProcessInstructionIntent.COMPLETE`
- [x] **AuthorizationCreateProcessor**
  - Intent: `AuthorizationIntent.CREATE`
- [x] **AuthorizationDeleteProcessor**
  - Intent: `AuthorizationIntent.DELETE`
- [x] **AuthorizationUpdateProcessor**
  - Intent: `AuthorizationIntent.UPDATE`
- [x] **BatchOperationCancelProcessor**
  - Intent: `BatchOperationIntent.CANCEL`
- [x] **BatchOperationCreateChunkProcessor**
  - Intent: `BatchOperationChunkIntent.CREATE`
- [x] **BatchOperationCreateProcessor**
  - Intent: `BatchOperationIntent.CREATE`
- [x] **BatchOperationExecuteProcessor**
  - Intent: `BatchOperationExecutionIntent.EXECUTE`
- [x] **BatchOperationFailProcessor**
  - Intent: `BatchOperationIntent.FAIL`
- [x] **BatchOperationFinishInitializationProcessor**
  - Intent: `BatchOperationIntent.FINISH_INITIALIZATION`
- [x] **BatchOperationInitializeProcessor**
  - Intent: `BatchOperationIntent.INITIALIZE`
- [x] **BatchOperationLeadPartitionCompleteProcessor**
  - Intent: `BatchOperationIntent.COMPLETE_PARTITION`
- [x] **BatchOperationLeadPartitionFailProcessor**
  - Intent: `BatchOperationIntent.FAIL_PARTITION`
- [x] **BatchOperationResumeProcessor**
  - Intent: `BatchOperationIntent.RESUME`
- [x] **BatchOperationSuspendProcessor**
  - Intent: `BatchOperationIntent.SUSPEND`
- [x] **BpmnStreamProcessor**
  - Intent: `ProcessInstanceIntent.ACTIVATE_ELEMENT`
  - Intent: `ProcessInstanceIntent.COMPLETE_ELEMENT`
  - Intent: `ProcessInstanceIntent.TERMINATE_ELEMENT`
  - Intent: `ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER`
  - Intent: `ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT`
  - Note: This processor handles multiple BPMN element commands dynamically
- [x] **ClockProcessor**
  - Intent: `ClockIntent.PIN`
  - Intent: `ClockIntent.RESET`
  - Note: Same processor instance handles both clock intents
- [x] **CommandDistributionAcknowledgeProcessor**
  - Intent: `CommandDistributionIntent.ACKNOWLEDGE`
- [x] **CommandDistributionContinueProcessor**
  - Intent: `CommandDistributionIntent.CONTINUE`
- [x] **CommandDistributionFinishProcessor**
  - Intent: `CommandDistributionIntent.FINISH`
- [x] **GroupAddEntityProcessor**
  - Intent: `GroupIntent.ADD_ENTITY`
- [x] **GroupCreateProcessor**
  - Intent: `GroupIntent.CREATE`
- [x] **GroupDeleteProcessor**
  - Intent: `GroupIntent.DELETE`
- [x] **GroupRemoveEntityProcessor**
  - Intent: `GroupIntent.REMOVE_ENTITY`
- [x] **GroupUpdateProcessor**
  - Intent: `GroupIntent.UPDATE`
- [x] **IdentitySetupInitializeProcessor**
  - Intent: `IdentitySetupIntent.INITIALIZE`
- [x] **IncidentResolveProcessor**
  - Intent: `IncidentIntent.RESOLVE`
- [x] **JobBatchActivateProcessor**
  - Intent: `JobBatchIntent.ACTIVATE`
- [x] **JobCancelProcessor**
  - Intent: `JobIntent.CANCEL`
- [x] **JobCompleteProcessor**
  - Intent: `JobIntent.COMPLETE`
- [x] **JobFailProcessor**
  - Intent: `JobIntent.FAIL`
- [x] **JobRecurProcessor**
  - Intent: `JobIntent.RECUR_AFTER_BACKOFF`
- [x] **JobThrowErrorProcessor**
  - Intent: `JobIntent.THROW_ERROR`
- [x] **JobTimeOutProcessor**
  - Intent: `JobIntent.TIME_OUT`
- [x] **JobUpdateProcessor**
  - Intent: `JobIntent.UPDATE`
- [x] **JobUpdateRetriesProcessor**
  - Intent: `JobIntent.UPDATE_RETRIES`
- [x] **JobUpdateTimeoutProcessor**
  - Intent: `JobIntent.UPDATE_TIMEOUT`
- [x] **JobYieldProcessor**
  - Intent: `JobIntent.YIELD`
- [x] **MappingRuleCreateProcessor**
  - Intent: `MappingRuleIntent.CREATE`
- [x] **MappingRuleDeleteProcessor**
  - Intent: `MappingRuleIntent.DELETE`
- [x] **MappingRuleUpdateProcessor**
  - Intent: `MappingRuleIntent.UPDATE`
- [x] **MarkPartitionBootstrappedProcessor**
  - Intent: `ScaleIntent.MARK_PARTITION_BOOTSTRAPPED`
- [x] **MessageBatchExpireProcessor**
  - Intent: `MessageBatchIntent.EXPIRE`
- [x] **MessageCorrelationCorrelateProcessor**
  - Intent: `MessageCorrelationIntent.CORRELATE`
- [x] **MessageExpireProcessor**
  - Intent: `MessageIntent.EXPIRE`
- [x] **MessagePublishProcessor**
  - Intent: `MessageIntent.PUBLISH`
- [x] **MessageSubscriptionCorrelateProcessor**
  - Intent: `MessageSubscriptionIntent.CORRELATE`
- [x] **MessageSubscriptionCreateProcessor**
  - Intent: `MessageSubscriptionIntent.CREATE`
- [x] **MessageSubscriptionDeleteProcessor**
  - Intent: `MessageSubscriptionIntent.DELETE`
- [x] **MessageSubscriptionMigrateProcessor**
  - Intent: `MessageSubscriptionIntent.MIGRATE`
- [x] **MessageSubscriptionRejectProcessor**
  - Intent: `MessageSubscriptionIntent.REJECT`
- [x] **ProcessInstanceBatchActivateProcessor**
  - Intent: `ProcessInstanceBatchIntent.ACTIVATE`
- [x] **ProcessInstanceBatchTerminateProcessor**
  - Intent: `ProcessInstanceBatchIntent.TERMINATE`
- [x] **ProcessInstanceCancelProcessor**
  - Intent: `ProcessInstanceIntent.CANCEL`
- [x] **ProcessInstanceCreationCreateWithResultProcessor**
  - Intent: `ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT`
- [x] **ProcessInstanceMigrationMigrateProcessor**
  - Intent: `ProcessInstanceMigrationIntent.MIGRATE`
- [x] **ProcessMessageSubscriptionCorrelateProcessor**
  - Intent: `ProcessMessageSubscriptionIntent.CORRELATE`
- [x] **ProcessMessageSubscriptionCreateProcessor**
  - Intent: `ProcessMessageSubscriptionIntent.CREATE`
- [x] **ProcessMessageSubscriptionDeleteProcessor**
  - Intent: `ProcessMessageSubscriptionIntent.DELETE`
- [x] **RoleAddEntityProcessor**
  - Intent: `RoleIntent.ADD_ENTITY`
- [x] **RoleCreateProcessor**
  - Intent: `RoleIntent.CREATE`
- [x] **RoleDeleteProcessor**
  - Intent: `RoleIntent.DELETE`
- [x] **RoleRemoveEntityProcessor**
  - Intent: `RoleIntent.REMOVE_ENTITY`
- [x] **RoleUpdateProcessor**
  - Intent: `RoleIntent.UPDATE`
- [x] **ScaleUpProcessor**
  - Intent: `ScaleIntent.SCALE_UP`
- [x] **ScaleUpStatusProcessor**
  - Intent: `ScaleIntent.STATUS`
- [x] **TenantAddEntityProcessor**
  - Intent: `TenantIntent.ADD_ENTITY`
- [x] **TenantCreateProcessor**
  - Intent: `TenantIntent.CREATE`
- [x] **TenantDeleteProcessor**
  - Intent: `TenantIntent.DELETE`
- [x] **TenantRemoveEntityProcessor**
  - Intent: `TenantIntent.REMOVE_ENTITY`
- [x] **TenantUpdateProcessor**
  - Intent: `TenantIntent.UPDATE`
- [x] **TimerCancelProcessor**
  - Intent: `TimerIntent.CANCEL`
- [x] **TimerTriggerProcessor**
  - Intent: `TimerIntent.TRIGGER`
- [x] **UsageMetricsExportProcessor**
  - Intent: `UsageMetricIntent.EXPORT`
- [x] **UserCreateInitialAdminProcessor**
  - Intent: `UserIntent.CREATE_INITIAL_ADMIN`
- [x] **UserCreateProcessor**
  - Intent: `UserIntent.CREATE`
- [x] **UserDeleteProcessor**
  - Intent: `UserIntent.DELETE`
- [x] **UserTaskProcessor**
  - Intent: `UserTaskIntent.COMPLETE`
  - Intent: `UserTaskIntent.ASSIGN`
  - Intent: `UserTaskIntent.CLAIM`
  - Intent: `UserTaskIntent.UPDATE`
  - Intent: `UserTaskIntent.COMPLETE_TASK_LISTENER`
  - Intent: `UserTaskIntent.DENY_TASK_LISTENER`
  - Note: Handles all UserTaskIntent command intents (non-event intents)
- [x] **UserUpdateProcessor**
  - Intent: `UserIntent.UPDATE`
- [x] **VariableDocumentUpdateProcessor**
  - Intent: `VariableDocumentIntent.UPDATE`

**Total Processors: 81** (78 regular processors + 1 BpmnStreamProcessor handling 5 intents + 1 ClockProcessor handling 2 intents + 1 UserTaskProcessor handling 6 intents)

## 2. Event Appliers

Event appliers in the `zeebe/engine` module that implement `TypedEventApplier` interface.

- [ ] **AdHocSubProcessInstructionCompletedApplier**
  - Intent: `AdHocSubProcessInstructionIntent.COMPLETED`
- [ ] **AsyncRequestProcessedApplier**
  - Intent: `AsyncRequestIntent.PROCESSED`
- [ ] **AsyncRequestReceivedApplier**
  - Intent: `AsyncRequestIntent.RECEIVED`
- [ ] **AuthorizationCreatedApplier**
  - Intent: `AuthorizationIntent.CREATED`
- [ ] **AuthorizationDeletedApplier**
  - Intent: `AuthorizationIntent.DELETED`
- [ ] **AuthorizationUpdatedApplier**
  - Intent: `AuthorizationIntent.UPDATED`
- [ ] **BatchOperatioSuspendedApplier**
  - Intent: `BatchOperationIntent.SUSPENDED`
- [ ] **BatchOperationCanceledApplier**
  - Intent: `BatchOperationIntent.CANCELED`
- [ ] **BatchOperationChunkCreatedApplier**
  - Intent: `BatchOperationChunkIntent.CREATED`
- [ ] **BatchOperationCompletedApplier**
  - Intent: `BatchOperationIntent.COMPLETED`
- [ ] **BatchOperationCreatedApplier**
  - Intent: `BatchOperationIntent.CREATED`
- [ ] **BatchOperationExecutingApplier**
  - Intent: `BatchOperationExecutionIntent.EXECUTING`
- [ ] **BatchOperationFailedApplier**
  - Intent: `BatchOperationIntent.FAILED`
- [ ] **BatchOperationInitializedApplier**
  - Intent: `BatchOperationIntent.INITIALIZED`
- [ ] **BatchOperationInitializingApplier**
  - Intent: `BatchOperationIntent.INITIALIZING`
- [ ] **BatchOperationPartitionCompletedApplier**
  - Intent: `BatchOperationIntent.PARTITION_COMPLETED`
- [ ] **BatchOperationPartitionFailedApplier**
  - Intent: `BatchOperationIntent.PARTITION_FAILED`
- [ ] **BatchOperationResumedApplier**
  - Intent: `BatchOperationIntent.RESUMED`
- [ ] **ClockPinnedApplier**
  - Intent: `ClockIntent.PINNED`
- [ ] **ClockResettedApplier**
  - Intent: `ClockIntent.RESETTED`
- [ ] **CommandDistributionAcknowledgedApplier**
  - Intent: `CommandDistributionIntent.ACKNOWLEDGED`
- [ ] **CommandDistributionContinuationRequestedApplier**
  - Intent: `CommandDistributionIntent.CONTINUATION_REQUESTED`
- [ ] **CommandDistributionContinuedApplier**
  - Intent: `CommandDistributionIntent.CONTINUED`
- [ ] **CommandDistributionDistributingApplier**
  - Intent: `CommandDistributionIntent.DISTRIBUTING`
- [ ] **CommandDistributionEnqueuedApplier**
  - Intent: `CommandDistributionIntent.ENQUEUED`
- [ ] **CommandDistributionFinishedApplier**
  - Intent: `CommandDistributionIntent.FINISHED`
- [ ] **CommandDistributionStartedApplier**
  - Intent: `CommandDistributionIntent.STARTED`
- [ ] **CompensationSubscriptionCompletedApplier**
  - Intent: `CompensationSubscriptionIntent.COMPLETED`
- [ ] **CompensationSubscriptionCreatedApplier**
  - Intent: `CompensationSubscriptionIntent.CREATED`
- [ ] **CompensationSubscriptionDeletedApplier**
  - Intent: `CompensationSubscriptionIntent.DELETED`
- [ ] **CompensationSubscriptionMigratedApplier**
  - Intent: `CompensationSubscriptionIntent.MIGRATED`
- [ ] **CompensationSubscriptionTriggeredApplier**
  - Intent: `CompensationSubscriptionIntent.TRIGGERED`
- [ ] **DecisionCreatedV1Applier**
  - Intent: `DecisionIntent.CREATED`
- [ ] **DecisionCreatedV2Applier**
  - Intent: `DecisionIntent.CREATED` (version=2)
- [ ] **DecisionDeletedApplier**
  - Intent: `DecisionIntent.DELETED`
- [ ] **DecisionEvaluationV2Applier**
  - Intent: `DecisionEvaluationIntent.EVALUATED` (version=2)
- [ ] **DecisionRequirementsCreatedApplier**
  - Intent: `DecisionRequirementsIntent.CREATED`
- [ ] **DecisionRequirementsDeletedApplier**
  - Intent: `DecisionRequirementsIntent.DELETED`
- [ ] **DeploymentCreatedV1Applier**
  - Intent: `DeploymentIntent.CREATED`
- [ ] **DeploymentCreatedV3Applier**
  - Intent: `DeploymentIntent.CREATED` (version=3)
- [ ] **DeploymentDistributedApplier**
  - Intent: `DeploymentIntent.DISTRIBUTED`
- [ ] **DeploymentDistributionApplier**
  - Intent: `DeploymentDistributionIntent.DISTRIBUTING`
- [ ] **DeploymentDistributionCompletedApplier**
  - Intent: `DeploymentDistributionIntent.COMPLETED`
- [ ] **DeploymentFullyDistributedApplier**
  - Intent: `DeploymentIntent.FULLY_DISTRIBUTED`
- [ ] **DeploymentReconstructedAllApplier**
  - Intent: `DeploymentIntent.RECONSTRUCTED_ALL`
- [ ] **DeploymentReconstructedApplier**
  - Intent: `DeploymentIntent.RECONSTRUCTED`
- [ ] **ErrorCreatedApplier**
  - Intent: `ErrorIntent.CREATED`
- [ ] **FormCreatedV1Applier**
  - Intent: `FormIntent.CREATED`
- [ ] **FormCreatedV2Applier**
  - Intent: `FormIntent.CREATED` (version=2)
- [ ] **FormDeletedApplier**
  - Intent: `FormIntent.DELETED`
- [ ] **GroupCreatedApplier**
  - Intent: `GroupIntent.CREATED`
- [ ] **GroupDeletedApplier**
  - Intent: `GroupIntent.DELETED`
- [ ] **GroupEntityAddedApplier**
  - Intent: `GroupIntent.ENTITY_ADDED`
- [ ] **GroupEntityRemovedApplier**
  - Intent: `GroupIntent.ENTITY_REMOVED`
- [ ] **GroupUpdatedApplier**
  - Intent: `GroupIntent.UPDATED`
- [ ] **IncidentCreatedApplier**
  - Intent: `IncidentIntent.CREATED`
- [ ] **IncidentMigratedApplier**
  - Intent: `IncidentIntent.MIGRATED`
- [ ] **IncidentResolvedV1Applier**
  - Intent: `IncidentIntent.RESOLVED`
- [ ] **IncidentResolvedV2Applier**
  - Intent: `IncidentIntent.RESOLVED` (version=2)
- [ ] **JobBatchActivatedApplier**
  - Intent: `JobBatchIntent.ACTIVATED`
- [ ] **JobCanceledApplier**
  - Intent: `JobIntent.CANCELED`
- [ ] **JobCompletedV1Applier**
  - Intent: `JobIntent.COMPLETED`
- [ ] **JobCreatedApplier**
  - Intent: `JobIntent.CREATED`
- [ ] **JobErrorThrownApplier**
  - Intent: `JobIntent.ERROR_THROWN`
- [ ] **JobFailedApplier**
  - Intent: `JobIntent.FAILED`
- [ ] **JobMigratedApplier**
  - Intent: `JobIntent.MIGRATED`
- [ ] **JobRecurredApplier**
  - Intent: `JobIntent.RECURRED_AFTER_BACKOFF`
- [ ] **JobRetriesUpdatedApplier**
  - Intent: `JobIntent.RETRIES_UPDATED`
- [ ] **JobTimedOutApplier**
  - Intent: `JobIntent.TIMED_OUT`
- [ ] **JobTimeoutUpdatedApplier**
  - Intent: `JobIntent.TIMEOUT_UPDATED`
- [ ] **JobUpdatedApplier**
  - Intent: `JobIntent.UPDATED`
- [ ] **JobYieldedApplier**
  - Intent: `JobIntent.YIELDED`
- [ ] **MappingRuleCreatedApplier**
  - Intent: `MappingRuleIntent.CREATED`
- [ ] **MappingRuleDeletedApplier**
  - Intent: `MappingRuleIntent.DELETED`
- [ ] **MappingRuleUpdatedApplier**
  - Intent: `MappingRuleIntent.UPDATED`
- [ ] **MessageCorrelationCorrelatedApplier**
  - Intent: `MessageCorrelationIntent.CORRELATED`
- [ ] **MessageCorrelationCorrelatingApplier**
  - Intent: `MessageCorrelationIntent.CORRELATING`
- [ ] **MessageCorrelationNotCorrelatedApplier**
  - Intent: `MessageCorrelationIntent.NOT_CORRELATED`
- [ ] **MessageExpiredApplier**
  - Intent: `MessageIntent.EXPIRED`
- [ ] **MessagePublishedApplier**
  - Intent: `MessageIntent.PUBLISHED`
- [ ] **MessageStartEventSubscriptionCorrelatedApplier**
  - Intent: `MessageStartEventSubscriptionIntent.CORRELATED`
- [ ] **MessageStartEventSubscriptionCreatedApplier**
  - Intent: `MessageStartEventSubscriptionIntent.CREATED`
- [ ] **MessageStartEventSubscriptionDeletedApplier**
  - Intent: `MessageStartEventSubscriptionIntent.DELETED`
- [ ] **MessageSubscriptionCorrelatedApplier**
  - Intent: `MessageSubscriptionIntent.CORRELATED`
- [ ] **MessageSubscriptionCorrelatingApplier**
  - Intent: `MessageSubscriptionIntent.CORRELATING`
- [ ] **MessageSubscriptionCreatedApplier**
  - Intent: `MessageSubscriptionIntent.CREATED`
- [ ] **MessageSubscriptionDeletedApplier**
  - Intent: `MessageSubscriptionIntent.DELETED`
- [ ] **MessageSubscriptionMigratedApplier**
  - Intent: `MessageSubscriptionIntent.MIGRATED`
- [ ] **MessageSubscriptionRejectedApplier**
  - Intent: `MessageSubscriptionIntent.REJECTED`
- [ ] **MultiInstanceInputCollectionEvaluatedApplier**
  - Intent: `MultiInstanceIntent.INPUT_COLLECTION_EVALUATED`
- [ ] **PartitionBootstrappedApplier**
  - Intent: `ScaleIntent.PARTITION_BOOTSTRAPPED`
- [ ] **ProcessCreatedV1Applier**
  - Intent: `ProcessIntent.CREATED`
- [ ] **ProcessCreatedV2Applier**
  - Intent: `ProcessIntent.CREATED` (version=2)
- [ ] **ProcessDeletedApplier**
  - Intent: `ProcessIntent.DELETED`
- [ ] **ProcessDeletingApplier**
  - Intent: `ProcessIntent.DELETING`
- [ ] **ProcessEventTriggeredApplier**
  - Intent: `ProcessEventIntent.TRIGGERED`
- [ ] **ProcessEventTriggeringApplier**
  - Intent: `ProcessEventIntent.TRIGGERING`
- [ ] **ProcessInstanceAncestorMigratedApplier**
  - Intent: `ProcessInstanceIntent.ANCESTOR_MIGRATED`
- [ ] **ProcessInstanceCreationCreatedV1Applier**
  - Intent: `ProcessInstanceCreationIntent.CREATED`
- [ ] **ProcessInstanceCreationCreatedV2Applier**
  - Intent: `ProcessInstanceCreationIntent.CREATED` (version=2)
- [ ] **ProcessInstanceElementActivatedApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_ACTIVATED`
- [ ] **ProcessInstanceElementActivatingV1Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_ACTIVATING`
- [ ] **ProcessInstanceElementActivatingV2Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_ACTIVATING` (version=2)
- [ ] **ProcessInstanceElementCompletedApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_COMPLETED`
- [ ] **ProcessInstanceElementCompletingApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_COMPLETING`
- [ ] **ProcessInstanceElementMigratedV1Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_MIGRATED`
- [ ] **ProcessInstanceElementMigratedV2Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_MIGRATED` (version=2)
- [ ] **ProcessInstanceElementTerminatedApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_TERMINATED`
- [ ] **ProcessInstanceElementTerminatingApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_TERMINATING`
- [ ] **ProcessInstanceModifiedEventApplier**
  - Intent: `ProcessInstanceModificationIntent.MODIFIED`
- [ ] **ProcessInstanceSequenceFlowDeletedApplier**
  - Intent: `ProcessInstanceIntent.SEQUENCE_FLOW_DELETED`
- [ ] **ProcessInstanceSequenceFlowTakenApplier**
  - Intent: `ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN`
- [ ] **ProcessMessageSubscriptionCorrelatedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.CORRELATED`
- [ ] **ProcessMessageSubscriptionCreatedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.CREATED`
- [ ] **ProcessMessageSubscriptionCreatingApplier**
  - Intent: `ProcessMessageSubscriptionIntent.CREATING`
- [ ] **ProcessMessageSubscriptionDeletedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.DELETED`
- [ ] **ProcessMessageSubscriptionDeletingApplier**
  - Intent: `ProcessMessageSubscriptionIntent.DELETING`
- [ ] **ProcessMessageSubscriptionMigratedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.MIGRATED`
- [ ] **ResourceCreatedApplier**
  - Intent: `ResourceIntent.CREATED`
- [ ] **ResourceDeletedApplier**
  - Intent: `ResourceIntent.DELETED`
- [ ] **RoleCreatedApplier**
  - Intent: `RoleIntent.CREATED`
- [ ] **RoleDeletedApplier**
  - Intent: `RoleIntent.DELETED`
- [ ] **RoleEntityAddedApplier**
  - Intent: `RoleIntent.ENTITY_ADDED`
- [ ] **RoleEntityRemovedApplier**
  - Intent: `RoleIntent.ENTITY_REMOVED`
- [ ] **RoleUpdatedApplier**
  - Intent: `RoleIntent.UPDATED`
- [ ] **RuntimeInstructionInterruptedApplier**
  - Intent: `RuntimeInstructionIntent.INTERRUPTED`
- [ ] **ScaleUpStatusResponseApplier**
  - Intent: `ScaleIntent.STATUS_RESPONSE`
- [ ] **ScaledUpApplier**
  - Intent: `ScaleIntent.SCALED_UP`
- [ ] **ScalingUpApplier**
  - Intent: `ScaleIntent.SCALING_UP`
- [ ] **SignalSubscriptionCreatedApplier**
  - Intent: `SignalSubscriptionIntent.CREATED`
- [ ] **SignalSubscriptionDeletedApplier**
  - Intent: `SignalSubscriptionIntent.DELETED`
- [ ] **SignalSubscriptionMigratedApplier**
  - Intent: `SignalSubscriptionIntent.MIGRATED`
- [ ] **TenantCreatedApplier**
  - Intent: `TenantIntent.CREATED`
- [ ] **TenantDeletedApplier**
  - Intent: `TenantIntent.DELETED`
- [ ] **TenantEntityAddedApplier**
  - Intent: `TenantIntent.ENTITY_ADDED`
- [ ] **TenantEntityRemovedApplier**
  - Intent: `TenantIntent.ENTITY_REMOVED`
- [ ] **TenantUpdatedApplier**
  - Intent: `TenantIntent.UPDATED`
- [ ] **TimerCancelledApplier**
  - Intent: `TimerIntent.CANCELED`
- [ ] **TimerCreatedApplier**
  - Intent: `TimerIntent.CREATED`
- [ ] **TimerInstanceMigratedApplier**
  - Intent: `TimerIntent.MIGRATED`
- [ ] **TimerTriggeredApplier**
  - Intent: `TimerIntent.TRIGGERED`
- [ ] **UsageMetricsExportedApplier**
  - Intent: `UsageMetricIntent.EXPORTED`
- [ ] **UserCreatedApplier**
  - Intent: `UserIntent.CREATED`
- [ ] **UserDeletedApplier**
  - Intent: `UserIntent.DELETED`
- [ ] **UserTaskAssignedV1Applier**
  - Intent: `UserTaskIntent.ASSIGNED`
- [ ] **UserTaskAssignedV2Applier**
  - Intent: `UserTaskIntent.ASSIGNED` (version=2)
- [ ] **UserTaskAssignedV3Applier**
  - Intent: `UserTaskIntent.ASSIGNED` (version=3)
- [ ] **UserTaskAssigningV1Applier**
  - Intent: `UserTaskIntent.ASSIGNING`
- [ ] **UserTaskAssigningV2Applier**
  - Intent: `UserTaskIntent.ASSIGNING` (version=2)
- [ ] **UserTaskAssignmentDeniedApplier**
  - Intent: `UserTaskIntent.ASSIGNMENT_DENIED`
- [ ] **UserTaskCanceledApplier**
  - Intent: `UserTaskIntent.CANCELED`
- [ ] **UserTaskCancelingV1Applier**
  - Intent: `UserTaskIntent.CANCELING`
- [ ] **UserTaskCancelingV2Applier**
  - Intent: `UserTaskIntent.CANCELING` (version=2)
- [ ] **UserTaskClaimingApplier**
  - Intent: `UserTaskIntent.CLAIMING`
- [ ] **UserTaskCompletedV1Applier**
  - Intent: `UserTaskIntent.COMPLETED`
- [ ] **UserTaskCompletedV2Applier**
  - Intent: `UserTaskIntent.COMPLETED` (version=2)
- [ ] **UserTaskCompletingV1Applier**
  - Intent: `UserTaskIntent.COMPLETING`
- [ ] **UserTaskCompletingV2Applier**
  - Intent: `UserTaskIntent.COMPLETING` (version=2)
- [ ] **UserTaskCompletionDeniedApplier**
  - Intent: `UserTaskIntent.COMPLETION_DENIED`
- [ ] **UserTaskCorrectedApplier**
  - Intent: `UserTaskIntent.CORRECTED`
- [ ] **UserTaskCreatedApplier**
  - Intent: `UserTaskIntent.CREATED`
- [ ] **UserTaskCreatedV2Applier**
  - Intent: `UserTaskIntent.CREATED` (version=2)
- [ ] **UserTaskCreatingApplier**
  - Intent: `UserTaskIntent.CREATING`
- [ ] **UserTaskCreatingV2Applier**
  - Intent: `UserTaskIntent.CREATING` (version=2)
- [ ] **UserTaskMigratedApplier**
  - Intent: `UserTaskIntent.MIGRATED`
- [ ] **UserTaskUpdateDeniedApplier**
  - Intent: `UserTaskIntent.UPDATE_DENIED`
- [ ] **UserTaskUpdatedV1Applier**
  - Intent: `UserTaskIntent.UPDATED`
- [ ] **UserTaskUpdatedV2Applier**
  - Intent: `UserTaskIntent.UPDATED` (version=2)
- [ ] **UserTaskUpdatingV1Applier**
  - Intent: `UserTaskIntent.UPDATING`
- [ ] **UserTaskUpdatingV2Applier**
  - Intent: `UserTaskIntent.UPDATING` (version=2)
- [ ] **UserUpdatedApplier**
  - Intent: `UserIntent.UPDATED`
- [ ] **VariableDocumentUpdateDeniedApplier**
  - Intent: `VariableDocumentIntent.UPDATE_DENIED`
- [ ] **VariableDocumentUpdatedApplier**
  - Intent: `VariableDocumentIntent.UPDATED`
- [ ] **VariableDocumentUpdatingApplier**
  - Intent: `VariableDocumentIntent.UPDATING`
- [ ] **VariableMigratedApplier**
  - Intent: `VariableIntent.MIGRATED`

**Total Event Appliers: 175**

## Summary

- **Processors**: 81 classes (78 regular + 3 multi-intent processors: BpmnStreamProcessor, ClockProcessor, UserTaskProcessor)
- **Event Appliers**: 175 classes
- **Total**: 256 classes to annotate

## Notes for Implementation

1. **For Processors and Event Appliers**: The intent mappings are straightforward from the registration code.

2. **For Export Handlers**: Some handlers use negative logic (e.g., IncidentHandler uses `!intent.equals(IncidentIntent.RESOLVED)`). For these handlers:

   - List all intents that are actually handled (the positive cases)
   - Add a note explaining the negative logic for clarity
   - Example: IncidentHandler handles CREATED and MIGRATED (all except RESOLVED)
3. **Version attribute**: Only use `version` attribute in the annotation when it's not the default value of 1.
4. **Multiple intents**: Use multiple `@HandlesIntent` annotations for handlers that support multiple intents (like BpmnStreamProcessor).
5. **Import statement needed**: Add `import io.camunda.zeebe.protocol.record.intent.HandlesIntent;` and `import io.camunda.zeebe.protocol.record.intent.HandlesIntents;` (for multiple annotations).

