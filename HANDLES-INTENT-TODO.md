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

- [x] **AdHocSubProcessInstructionCompletedApplier**
  - Intent: `AdHocSubProcessInstructionIntent.COMPLETED`
- [x] **AsyncRequestProcessedApplier**
  - Intent: `AsyncRequestIntent.PROCESSED`
- [x] **AsyncRequestReceivedApplier**
  - Intent: `AsyncRequestIntent.RECEIVED`
- [x] **AuthorizationCreatedApplier**
  - Intent: `AuthorizationIntent.CREATED`
- [x] **AuthorizationDeletedApplier**
  - Intent: `AuthorizationIntent.DELETED`
- [x] **AuthorizationUpdatedApplier**
  - Intent: `AuthorizationIntent.UPDATED`
- [x] **BatchOperatioSuspendedApplier**
  - Intent: `BatchOperationIntent.SUSPENDED`
- [x] **BatchOperationCanceledApplier**
  - Intent: `BatchOperationIntent.CANCELED`
- [x] **BatchOperationChunkCreatedApplier**
  - Intent: `BatchOperationChunkIntent.CREATED`
- [x] **BatchOperationCompletedApplier**
  - Intent: `BatchOperationIntent.COMPLETED`
- [x] **BatchOperationCreatedApplier**
  - Intent: `BatchOperationIntent.CREATED`
- [x] **BatchOperationExecutingApplier**
  - Intent: `BatchOperationExecutionIntent.EXECUTING`
- [x] **BatchOperationFailedApplier**
  - Intent: `BatchOperationIntent.FAILED`
- [x] **BatchOperationInitializedApplier**
  - Intent: `BatchOperationIntent.INITIALIZED`
- [x] **BatchOperationInitializingApplier**
  - Intent: `BatchOperationIntent.INITIALIZING`
- [x] **BatchOperationPartitionCompletedApplier**
  - Intent: `BatchOperationIntent.PARTITION_COMPLETED`
- [x] **BatchOperationPartitionFailedApplier**
  - Intent: `BatchOperationIntent.PARTITION_FAILED`
- [x] **BatchOperationResumedApplier**
  - Intent: `BatchOperationIntent.RESUMED`
- [x] **ClockPinnedApplier**
  - Intent: `ClockIntent.PINNED`
- [x] **ClockResettedApplier**
  - Intent: `ClockIntent.RESETTED`
- [x] **CommandDistributionAcknowledgedApplier**
  - Intent: `CommandDistributionIntent.ACKNOWLEDGED`
- [x] **CommandDistributionContinuationRequestedApplier**
  - Intent: `CommandDistributionIntent.CONTINUATION_REQUESTED`
- [x] **CommandDistributionContinuedApplier**
  - Intent: `CommandDistributionIntent.CONTINUED`
- [x] **CommandDistributionDistributingApplier**
  - Intent: `CommandDistributionIntent.DISTRIBUTING`
- [x] **CommandDistributionEnqueuedApplier**
  - Intent: `CommandDistributionIntent.ENQUEUED`
- [x] **CommandDistributionFinishedApplier**
  - Intent: `CommandDistributionIntent.FINISHED`
- [x] **CommandDistributionStartedApplier**
  - Intent: `CommandDistributionIntent.STARTED`
- [x] **CompensationSubscriptionCompletedApplier**
  - Intent: `CompensationSubscriptionIntent.COMPLETED`
- [x] **CompensationSubscriptionCreatedApplier**
  - Intent: `CompensationSubscriptionIntent.CREATED`
- [x] **CompensationSubscriptionDeletedApplier**
  - Intent: `CompensationSubscriptionIntent.DELETED`
- [x] **CompensationSubscriptionMigratedApplier**
  - Intent: `CompensationSubscriptionIntent.MIGRATED`
- [x] **CompensationSubscriptionTriggeredApplier**
  - Intent: `CompensationSubscriptionIntent.TRIGGERED`
- [x] **DecisionCreatedV1Applier**
  - Intent: `DecisionIntent.CREATED`
- [x] **DecisionCreatedV2Applier**
  - Intent: `DecisionIntent.CREATED` (version=2)
- [x] **DecisionDeletedApplier**
  - Intent: `DecisionIntent.DELETED`
- [x] **DecisionEvaluationV2Applier**
  - Intent: `DecisionEvaluationIntent.EVALUATED` (version=2)
- [x] **DecisionRequirementsCreatedApplier**
  - Intent: `DecisionRequirementsIntent.CREATED`
- [x] **DecisionRequirementsDeletedApplier**
  - Intent: `DecisionRequirementsIntent.DELETED`
- [x] **DeploymentCreatedV1Applier**
  - Intent: `DeploymentIntent.CREATED`
- [x] **DeploymentCreatedV3Applier**
  - Intent: `DeploymentIntent.CREATED` (version=3)
- [x] **DeploymentDistributedApplier**
  - Intent: `DeploymentIntent.DISTRIBUTED`
- [x] **DeploymentDistributionApplier**
  - Intent: `DeploymentDistributionIntent.DISTRIBUTING`
- [x] **DeploymentDistributionCompletedApplier**
  - Intent: `DeploymentDistributionIntent.COMPLETED`
- [x] **DeploymentFullyDistributedApplier**
  - Intent: `DeploymentIntent.FULLY_DISTRIBUTED`
- [x] **DeploymentReconstructedAllApplier**
  - Intent: `DeploymentIntent.RECONSTRUCTED_ALL`
- [x] **DeploymentReconstructedApplier**
  - Intent: `DeploymentIntent.RECONSTRUCTED`
- [x] **ErrorCreatedApplier**
  - Intent: `ErrorIntent.CREATED`
- [x] **FormCreatedV1Applier**
  - Intent: `FormIntent.CREATED`
- [x] **FormCreatedV2Applier**
  - Intent: `FormIntent.CREATED` (version=2)
- [x] **FormDeletedApplier**
  - Intent: `FormIntent.DELETED`
- [x] **GroupCreatedApplier**
  - Intent: `GroupIntent.CREATED`
- [x] **GroupDeletedApplier**
  - Intent: `GroupIntent.DELETED`
- [x] **GroupEntityAddedApplier**
  - Intent: `GroupIntent.ENTITY_ADDED`
- [x] **GroupEntityRemovedApplier**
  - Intent: `GroupIntent.ENTITY_REMOVED`
- [x] **GroupUpdatedApplier**
  - Intent: `GroupIntent.UPDATED`
- [x] **IncidentCreatedApplier**
  - Intent: `IncidentIntent.CREATED`
- [x] **IncidentMigratedApplier**
  - Intent: `IncidentIntent.MIGRATED`
- [x] **IncidentResolvedV1Applier**
  - Intent: `IncidentIntent.RESOLVED`
- [x] **IncidentResolvedV2Applier**
  - Intent: `IncidentIntent.RESOLVED` (version=2)
- [x] **JobBatchActivatedApplier**
  - Intent: `JobBatchIntent.ACTIVATED`
- [x] **JobCanceledApplier**
  - Intent: `JobIntent.CANCELED`
- [x] **JobCompletedV1Applier**
  - Intent: `JobIntent.COMPLETED`
- [x] **JobCreatedApplier**
  - Intent: `JobIntent.CREATED`
- [x] **JobErrorThrownApplier**
  - Intent: `JobIntent.ERROR_THROWN`
- [x] **JobFailedApplier**
  - Intent: `JobIntent.FAILED`
- [x] **JobMigratedApplier**
  - Intent: `JobIntent.MIGRATED`
- [x] **JobRecurredApplier**
  - Intent: `JobIntent.RECURRED_AFTER_BACKOFF`
- [x] **JobRetriesUpdatedApplier**
  - Intent: `JobIntent.RETRIES_UPDATED`
- [x] **JobTimedOutApplier**
  - Intent: `JobIntent.TIMED_OUT`
- [x] **JobTimeoutUpdatedApplier**
  - Intent: `JobIntent.TIMEOUT_UPDATED`
- [x] **JobUpdatedApplier**
  - Intent: `JobIntent.UPDATED`
- [x] **JobYieldedApplier**
  - Intent: `JobIntent.YIELDED`
- [x] **MappingRuleCreatedApplier**
  - Intent: `MappingRuleIntent.CREATED`
- [x] **MappingRuleDeletedApplier**
  - Intent: `MappingRuleIntent.DELETED`
- [x] **MappingRuleUpdatedApplier**
  - Intent: `MappingRuleIntent.UPDATED`
- [x] **MessageCorrelationCorrelatedApplier**
  - Intent: `MessageCorrelationIntent.CORRELATED`
- [x] **MessageCorrelationCorrelatingApplier**
  - Intent: `MessageCorrelationIntent.CORRELATING`
- [x] **MessageCorrelationNotCorrelatedApplier**
  - Intent: `MessageCorrelationIntent.NOT_CORRELATED`
- [x] **MessageExpiredApplier**
  - Intent: `MessageIntent.EXPIRED`
- [x] **MessagePublishedApplier**
  - Intent: `MessageIntent.PUBLISHED`
- [x] **MessageStartEventSubscriptionCorrelatedApplier**
  - Intent: `MessageStartEventSubscriptionIntent.CORRELATED`
- [x] **MessageStartEventSubscriptionCreatedApplier**
  - Intent: `MessageStartEventSubscriptionIntent.CREATED`
- [x] **MessageStartEventSubscriptionDeletedApplier**
  - Intent: `MessageStartEventSubscriptionIntent.DELETED`
- [x] **MessageSubscriptionCorrelatedApplier**
  - Intent: `MessageSubscriptionIntent.CORRELATED`
- [x] **MessageSubscriptionCorrelatingApplier**
  - Intent: `MessageSubscriptionIntent.CORRELATING`
- [x] **MessageSubscriptionCreatedApplier**
  - Intent: `MessageSubscriptionIntent.CREATED`
- [x] **MessageSubscriptionDeletedApplier**
  - Intent: `MessageSubscriptionIntent.DELETED`
- [x] **MessageSubscriptionMigratedApplier**
  - Intent: `MessageSubscriptionIntent.MIGRATED`
- [x] **MessageSubscriptionRejectedApplier**
  - Intent: `MessageSubscriptionIntent.REJECTED`
- [x] **MultiInstanceInputCollectionEvaluatedApplier**
  - Intent: `MultiInstanceIntent.INPUT_COLLECTION_EVALUATED`
- [x] **PartitionBootstrappedApplier**
  - Intent: `ScaleIntent.PARTITION_BOOTSTRAPPED`
- [x] **ProcessCreatedV1Applier**
  - Intent: `ProcessIntent.CREATED`
- [x] **ProcessCreatedV2Applier**
  - Intent: `ProcessIntent.CREATED` (version=2)
- [x] **ProcessDeletedApplier**
  - Intent: `ProcessIntent.DELETED`
- [x] **ProcessDeletingApplier**
  - Intent: `ProcessIntent.DELETING`
- [x] **ProcessEventTriggeredApplier**
  - Intent: `ProcessEventIntent.TRIGGERED`
- [x] **ProcessEventTriggeringApplier**
  - Intent: `ProcessEventIntent.TRIGGERING`
- [x] **ProcessInstanceAncestorMigratedApplier**
  - Intent: `ProcessInstanceIntent.ANCESTOR_MIGRATED`
- [x] **ProcessInstanceCreationCreatedV1Applier**
  - Intent: `ProcessInstanceCreationIntent.CREATED`
- [x] **ProcessInstanceCreationCreatedV2Applier**
  - Intent: `ProcessInstanceCreationIntent.CREATED` (version=2)
- [x] **ProcessInstanceElementActivatedApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_ACTIVATED`
- [x] **ProcessInstanceElementActivatingV1Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_ACTIVATING`
- [x] **ProcessInstanceElementActivatingV2Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_ACTIVATING` (version=2)
- [x] **ProcessInstanceElementCompletedApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_COMPLETED`
- [x] **ProcessInstanceElementCompletingApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_COMPLETING`
- [x] **ProcessInstanceElementMigratedV1Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_MIGRATED`
- [x] **ProcessInstanceElementMigratedV2Applier**
  - Intent: `ProcessInstanceIntent.ELEMENT_MIGRATED` (version=2)
- [x] **ProcessInstanceElementTerminatedApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_TERMINATED`
- [x] **ProcessInstanceElementTerminatingApplier**
  - Intent: `ProcessInstanceIntent.ELEMENT_TERMINATING`
- [x] **ProcessInstanceModifiedEventApplier**
  - Intent: `ProcessInstanceModificationIntent.MODIFIED`
- [x] **ProcessInstanceSequenceFlowDeletedApplier**
  - Intent: `ProcessInstanceIntent.SEQUENCE_FLOW_DELETED`
- [x] **ProcessInstanceSequenceFlowTakenApplier**
  - Intent: `ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN`
- [x] **ProcessMessageSubscriptionCorrelatedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.CORRELATED`
- [x] **ProcessMessageSubscriptionCreatedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.CREATED`
- [x] **ProcessMessageSubscriptionCreatingApplier**
  - Intent: `ProcessMessageSubscriptionIntent.CREATING`
- [x] **ProcessMessageSubscriptionDeletedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.DELETED`
- [x] **ProcessMessageSubscriptionDeletingApplier**
  - Intent: `ProcessMessageSubscriptionIntent.DELETING`
- [x] **ProcessMessageSubscriptionMigratedApplier**
  - Intent: `ProcessMessageSubscriptionIntent.MIGRATED`
- [x] **ResourceCreatedApplier**
  - Intent: `ResourceIntent.CREATED`
- [x] **ResourceDeletedApplier**
  - Intent: `ResourceIntent.DELETED`
- [x] **RoleCreatedApplier**
  - Intent: `RoleIntent.CREATED`
- [x] **RoleDeletedApplier**
  - Intent: `RoleIntent.DELETED`
- [x] **RoleEntityAddedApplier**
  - Intent: `RoleIntent.ENTITY_ADDED`
- [x] **RoleEntityRemovedApplier**
  - Intent: `RoleIntent.ENTITY_REMOVED`
- [x] **RoleUpdatedApplier**
  - Intent: `RoleIntent.UPDATED`
- [x] **RuntimeInstructionInterruptedApplier**
  - Intent: `RuntimeInstructionIntent.INTERRUPTED`
- [x] **ScaleUpStatusResponseApplier**
  - Intent: `ScaleIntent.STATUS_RESPONSE`
- [x] **ScaledUpApplier**
  - Intent: `ScaleIntent.SCALED_UP`
- [x] **ScalingUpApplier**
  - Intent: `ScaleIntent.SCALING_UP`
- [x] **SignalSubscriptionCreatedApplier**
  - Intent: `SignalSubscriptionIntent.CREATED`
- [x] **SignalSubscriptionDeletedApplier**
  - Intent: `SignalSubscriptionIntent.DELETED`
- [x] **SignalSubscriptionMigratedApplier**
  - Intent: `SignalSubscriptionIntent.MIGRATED`
- [x] **TenantCreatedApplier**
  - Intent: `TenantIntent.CREATED`
- [x] **TenantDeletedApplier**
  - Intent: `TenantIntent.DELETED`
- [x] **TenantEntityAddedApplier**
  - Intent: `TenantIntent.ENTITY_ADDED`
- [x] **TenantEntityRemovedApplier**
  - Intent: `TenantIntent.ENTITY_REMOVED`
- [x] **TenantUpdatedApplier**
  - Intent: `TenantIntent.UPDATED`
- [x] **TimerCancelledApplier**
  - Intent: `TimerIntent.CANCELED`
- [x] **TimerCreatedApplier**
  - Intent: `TimerIntent.CREATED`
- [x] **TimerInstanceMigratedApplier**
  - Intent: `TimerIntent.MIGRATED`
- [x] **TimerTriggeredApplier**
  - Intent: `TimerIntent.TRIGGERED`
- [x] **UsageMetricsExportedApplier**
  - Intent: `UsageMetricIntent.EXPORTED`
- [x] **UserCreatedApplier**
  - Intent: `UserIntent.CREATED`
- [x] **UserDeletedApplier**
  - Intent: `UserIntent.DELETED`
- [x] **UserTaskAssignedV1Applier**
  - Intent: `UserTaskIntent.ASSIGNED`
- [x] **UserTaskAssignedV2Applier**
  - Intent: `UserTaskIntent.ASSIGNED` (version=2)
- [x] **UserTaskAssignedV3Applier**
  - Intent: `UserTaskIntent.ASSIGNED` (version=3)
- [x] **UserTaskAssigningV1Applier**
  - Intent: `UserTaskIntent.ASSIGNING`
- [x] **UserTaskAssigningV2Applier**
  - Intent: `UserTaskIntent.ASSIGNING` (version=2)
- [x] **UserTaskAssignmentDeniedApplier**
  - Intent: `UserTaskIntent.ASSIGNMENT_DENIED`
- [x] **UserTaskCanceledApplier**
  - Intent: `UserTaskIntent.CANCELED`
- [x] **UserTaskCancelingV1Applier**
  - Intent: `UserTaskIntent.CANCELING`
- [x] **UserTaskCancelingV2Applier**
  - Intent: `UserTaskIntent.CANCELING` (version=2)
- [x] **UserTaskClaimingApplier**
  - Intent: `UserTaskIntent.CLAIMING`
- [x] **UserTaskCompletedV1Applier**
  - Intent: `UserTaskIntent.COMPLETED`
- [x] **UserTaskCompletedV2Applier**
  - Intent: `UserTaskIntent.COMPLETED` (version=2)
- [x] **UserTaskCompletingV1Applier**
  - Intent: `UserTaskIntent.COMPLETING`
- [x] **UserTaskCompletingV2Applier**
  - Intent: `UserTaskIntent.COMPLETING` (version=2)
- [x] **UserTaskCompletionDeniedApplier**
  - Intent: `UserTaskIntent.COMPLETION_DENIED`
- [x] **UserTaskCorrectedApplier**
  - Intent: `UserTaskIntent.CORRECTED`
- [x] **UserTaskCreatedApplier**
  - Intent: `UserTaskIntent.CREATED`
- [x] **UserTaskCreatedV2Applier**
  - Intent: `UserTaskIntent.CREATED` (version=2)
- [x] **UserTaskCreatingApplier**
  - Intent: `UserTaskIntent.CREATING`
- [x] **UserTaskCreatingV2Applier**
  - Intent: `UserTaskIntent.CREATING` (version=2)
- [x] **UserTaskMigratedApplier**
  - Intent: `UserTaskIntent.MIGRATED`
- [x] **UserTaskUpdateDeniedApplier**
  - Intent: `UserTaskIntent.UPDATE_DENIED`
- [x] **UserTaskUpdatedV1Applier**
  - Intent: `UserTaskIntent.UPDATED`
- [x] **UserTaskUpdatedV2Applier**
  - Intent: `UserTaskIntent.UPDATED` (version=2)
- [x] **UserTaskUpdatingV1Applier**
  - Intent: `UserTaskIntent.UPDATING`
- [x] **UserTaskUpdatingV2Applier**
  - Intent: `UserTaskIntent.UPDATING` (version=2)
- [x] **UserUpdatedApplier**
  - Intent: `UserIntent.UPDATED`
- [x] **VariableDocumentUpdateDeniedApplier**
  - Intent: `VariableDocumentIntent.UPDATE_DENIED`
- [x] **VariableDocumentUpdatedApplier**
  - Intent: `VariableDocumentIntent.UPDATED`
- [x] **VariableDocumentUpdatingApplier**
  - Intent: `VariableDocumentIntent.UPDATING`
- [x] **VariableMigratedApplier**
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

