/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.engine.state.immutable.TriggeringRecordMetadataState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.instance.UserTaskIntermediateStateValue;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class UserTaskCancelingV2ApplierTest {

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  /** The class under test. */
  private UserTaskCancelingV2Applier userTaskCancelingApplier;

  /** Used for state assertions. */
  private MutableUserTaskState userTaskState;

  private TriggeringRecordMetadataState recordMetadataState;

  /** For setting up the state before testing the applier. */
  private AppliersTestSetupHelper testSetup;

  @BeforeEach
  public void setup() {
    userTaskCancelingApplier = new UserTaskCancelingV2Applier(processingState);
    userTaskState = processingState.getUserTaskState();
    recordMetadataState = processingState.getTriggeringRecordMetadataState();
    testSetup = new AppliersTestSetupHelper(processingState);
  }

  @Test
  void shouldStoreCancelingStatesWhenNoOngoingTransition() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();

    final var userTaskRecord =
        new UserTaskRecord().setUserTaskKey(userTaskKey).setElementInstanceKey(elementInstanceKey);

    // simulate user task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);

    // preconditions: no dirty state should exist
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to be in CREATED state before applying CANCELING")
        .isEqualTo(LifecycleState.CREATED);
    assertThat(recordMetadataState.findOnly(userTaskKey, ValueType.USER_TASK))
        .describedAs("Expected no record metadata before canceling")
        .isEmpty();

    // when
    userTaskCancelingApplier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to transition to CANCELING state")
        .isEqualTo(LifecycleState.CANCELING);
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expected new intermediate state to be related to 'cancel' transition")
        .extracting(UserTaskIntermediateStateValue::getLifecycleState)
        .isEqualTo(LifecycleState.CANCELING);
  }

  @Test
  void shouldCleanupOngoingUpdateTransitionTriggeredByTaskVariablesUpdateAndStoreCancelingStates() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();
    final long variableDocumentKey = new Random().nextLong();

    final var variablesBuffer = MsgPackUtil.asMsgPack(Map.of("status", "approved"));

    final var userTaskRecord =
        new UserTaskRecord().setUserTaskKey(userTaskKey).setElementInstanceKey(elementInstanceKey);

    final var variableDocumentRecord =
        new VariableDocumentRecord()
            .setScopeKey(elementInstanceKey)
            .setVariables(variablesBuffer)
            .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL);

    // simulate user task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);

    // simulate a `VariableDocument.UPDATE` triggering a user task update
    testSetup.applyEventToState(
        variableDocumentKey, VariableDocumentIntent.UPDATING, variableDocumentRecord);
    testSetup.applyEventToState(
        userTaskKey,
        UserTaskIntent.UPDATING,
        userTaskRecord.copy().setVariables(variablesBuffer).setVariablesChanged());

    // preconditions
    assertThat(processingState.getVariableState().findVariableDocumentState(elementInstanceKey))
        .describedAs("Expected variable document state to exist before user task cancellation")
        .isPresent();
    assertThat(recordMetadataState.findOnly(userTaskKey, ValueType.USER_TASK))
        .describedAs("Expected no record request metadata before canceling")
        .isEmpty();
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expected 'update' intermediate state to be persisted before canceling")
        .satisfies(
            state ->
                assertThat(state)
                    .describedAs("Expected intermediate state to be related to 'update' transition")
                    .extracting(UserTaskIntermediateStateValue::getLifecycleState)
                    .isEqualTo(LifecycleState.UPDATING));

    // when
    userTaskCancelingApplier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to transition to CANCELING state")
        .isEqualTo(LifecycleState.CANCELING);
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expected new intermediate state to be related to 'cancel' transition")
        .extracting(UserTaskIntermediateStateValue::getLifecycleState)
        .isEqualTo(LifecycleState.CANCELING);
    assertThat(processingState.getVariableState().findVariableDocumentState(elementInstanceKey))
        .describedAs("Expected variable document state to be removed on canceling")
        .isEmpty();
  }

  @Test
  void shouldCleanupOngoingClaimTransitionAndStoreCancelingStates() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();

    final var userTaskRecord =
        new UserTaskRecord().setUserTaskKey(userTaskKey).setElementInstanceKey(elementInstanceKey);

    // simulate user task creation
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);

    // simulate user task assignment and persisting request metadata
    final UserTaskRecord claimRecord = userTaskRecord.copy().setAssignee("john");
    final var claimCommand =
        new MockTypedRecord<>(
            userTaskKey,
            new RecordMetadata()
                .intent(UserTaskIntent.CLAIM)
                .valueType(ValueType.USER_TASK)
                .requestId(11)
                .requestStreamId(22),
            claimRecord);
    testSetup.applyEventToState(
        userTaskKey,
        UserTaskIntent.CLAIMING,
        claimRecord,
        TriggeringRecordMetadata.from(claimCommand));

    // preconditions
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expected 'claim' intermediate state to be persisted before canceling")
        .satisfies(
            state ->
                assertThat(state)
                    .describedAs("Expected intermediate state to be related to 'claim' transition")
                    .extracting(UserTaskIntermediateStateValue::getLifecycleState)
                    .isEqualTo(LifecycleState.CLAIMING))
        .satisfies(
            state ->
                Assertions.assertThat(state.getRecord())
                    .describedAs("Expected record in intermediate to have previous transition data")
                    .hasAssignee("john"));
    assertThat(recordMetadataState.findOnly(userTaskKey, ValueType.USER_TASK))
        .hasValueSatisfying(
            metadata -> assertThat(metadata.getIntent()).isEqualTo(UserTaskIntent.CLAIM));

    // when
    userTaskCancelingApplier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.getLifecycleState(userTaskKey))
        .describedAs("Expected user task to transition to CANCELING state")
        .isEqualTo(LifecycleState.CANCELING);
    assertThat(userTaskState.getIntermediateState(userTaskKey))
        .describedAs("Expected new intermediate state to be related to 'cancel' transition")
        .extracting(UserTaskIntermediateStateValue::getLifecycleState)
        .isEqualTo(LifecycleState.CANCELING);
    assertThat(recordMetadataState.findOnly(userTaskKey, ValueType.USER_TASK))
        .describedAs("Expected record record metadata to be removed on canceling")
        .isEmpty();
  }

  @Test
  public void shouldClearInitialAssigneeOnUserTaskCancelingTransition() {
    // given
    final long userTaskKey = new Random().nextLong();
    final long elementInstanceKey = new Random().nextLong();
    final String initialAssignee = "initial_assignee";

    final var userTaskRecord =
        new UserTaskRecord()
            .setUserTaskKey(userTaskKey)
            .setAssignee(initialAssignee)
            .setElementInstanceKey(elementInstanceKey);

    // simulate user task creation
    // assignee is present in the creating event
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATING, userTaskRecord);
    // but we clear the assignee for created event
    final UserTaskRecord recordWithoutAssignee = userTaskRecord.unsetAssignee();
    testSetup.applyEventToState(userTaskKey, UserTaskIntent.CREATED, recordWithoutAssignee);

    assertThat(userTaskState.findInitialAssignee(userTaskKey))
        .describedAs("Expect initial assignee to be present")
        .isEqualTo(Optional.of(initialAssignee));

    // when
    userTaskCancelingApplier.applyState(userTaskKey, userTaskRecord);

    // then
    assertThat(userTaskState.findInitialAssignee(userTaskKey))
        .describedAs("Expect initial assignee to be cleaned up")
        .isEmpty();
  }
}
