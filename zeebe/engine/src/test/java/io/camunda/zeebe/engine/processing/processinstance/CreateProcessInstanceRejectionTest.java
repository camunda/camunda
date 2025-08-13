/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.ByteValue;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

public class CreateProcessInstanceRejectionTest {

  private static final long MAX_MESSAGE_SIZE = ByteValue.ofMegabytes(4);
  private static final String PROCESS_ID = "process-id";
  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCommandIfElementIdIsUnknown() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .manualTask("task")
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withStartInstruction("task")
        .withStartInstruction("unknown-element")
        .withVariable("x", 1)
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create instance of process with start instructions but no element found with id 'unknown-element'.");
  }

  @Test
  public void shouldRejectCommandIfElementIsInsideMultiInstance() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .manualTask("task-in-multi-instance")
                            .done())
                .multiInstance(m -> m.zeebeInputCollectionExpression("[1,2,3]"))
                .manualTask("task")
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withStartInstruction("task")
        .withStartInstruction("task-in-multi-instance")
        .withVariable("x", 1)
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create instance of process with start instructions but the element with id 'task-in-multi-instance' is inside a multi-instance subprocess. The creation of elements inside a multi-instance subprocess is not supported.");
  }

  @Test
  public void shouldRejectCommandIfEventBelongsToEventBasedGateway() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("start")
                .eventBasedGateway()
                .intermediateCatchEvent("timer1", c -> c.timerWithDuration("PT0.1S"))
                .endEvent()
                .moveToLastGateway()
                .intermediateCatchEvent("timer2", c -> c.timerWithDuration("PT0.1S"))
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withStartInstruction("timer1")
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create instance of process with start instructions but the element with id 'timer1' belongs to an event-based gateway. The creation of elements belonging to an event-based gateway is not supported.");
  }

  @Test
  public void shouldRejectCommandIfUnableToSubscribeToEvents() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    subprocess -> {
                      subprocess
                          .embeddedSubProcess()
                          .startEvent()
                          .serviceTask("task", t -> t.zeebeJobType("task"))
                          .endEvent();

                      subprocess
                          .boundaryEvent("message-boundary-event")
                          .cancelActivity(false)
                          .message(m -> m.name("msg").zeebeCorrelationKeyExpression("unknown_var"))
                          .endEvent();
                    })
                .endEvent()
                .done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withStartInstruction("task")
        .expectRejection()
        .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withBpmnProcessId(PROCESS_ID)
                .withStartInstruction("task")
                .onlyCommandRejections()
                .getFirst())
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to subscribe to catch event(s) of 'subprocess' but \
            Failed to extract the correlation key for 'unknown_var': \
            The value must be either a string or a number, but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'unknown_var'""");

    Assertions.assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getRecordType() == RecordType.COMMAND_REJECTION
                            && r.getIntent() == ProcessInstanceCreationIntent.CREATE))
        .extracting(Record::getValueType, Record::getIntent)
        .describedAs("Expect that no process instance is activated")
        .doesNotContain(
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldRejectCommandIfTooLarge() {
    // given
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariable("variable", "x".repeat((int) (MAX_MESSAGE_SIZE - ByteValue.ofKilobytes(1))))
        .expectRejection()
        .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .withBpmnProcessId(PROCESS_ID)
                .onlyCommandRejections()
                .getFirst())
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.EXCEEDED_BATCH_RECORD_SIZE);
  }

  @Test
  public void shouldRejectCommandIfNoProcessDefinitionForTenant() {
    // given
    final String processId = "process";
    final String tenantId = "foo";
    final String fakeTenantId = "bar";
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .withTenantId(tenantId)
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withTenantId(fakeTenantId)
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to find process definition with process ID '%s', but none found",
                processId, fakeTenantId));
  }

  @Test
  public void shouldRejectCommandIfTagsAreInvalid() {
    // given
    final String processId = "process";
    final String invalidTag = "this is not a valid tag";
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withTags(invalidTag)
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                "Expected to create instance of process with tags, but tag '%s' is invalid. Tag must start with a letter (a-z, A-Z), followed by alphanumerics, underscores, minuses, colons, or periods. It must not be blank and must be 100 characters or less.",
                invalidTag));
  }

  @Test
  public void shouldRejectCommandIfTooManyTagsProvided() {
    // given
    final String processId = "process";
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .withTags("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k")
        .expectRejection()
        .create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create instance of process with tags, but the number of tags exceeds the limit of 10.");
  }
}
