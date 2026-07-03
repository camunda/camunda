/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.suspension.BufferedCommand;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Characterization benchmark for the process instance suspend/resume POC (#56552), track (a)
 * scenario 3: storage growth.
 *
 * <p>Not part of the regular test suite — no assertions or thresholds, only measurements printed to
 * stdout for a human to read. Run explicitly:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/engine \
 *   -Dtest=ProcessInstanceSuspendStorageGrowthBenchmark -DskipTests=false -DskipITs -Dquickly
 * </pre>
 *
 * <p>1,000 instances per profile (not a count sweep — the first pass at this scenario swept
 * instance count 1..10,000 with one narrow command shape and found N=10,000 took over an hour,
 * dominated by {@code RecordingExporter}'s growing in-memory record list rather than anything the
 * engine itself does; 1,000 is enough to see per-profile byte cost without paying that cost per
 * profile). Instead this sweeps buffered-command *shape*: varying intent, BPMN element nesting
 * depth, tags/businessId length, since the first pass used one hardcoded {@code COMPLETE_ELEMENT}
 * shape and every entry came out an identical, uninformative 4,059 bytes.
 *
 * <p>{@code SUSPENDED_PROCESS_INSTANCES} and {@code BUFFERED_PROCESS_INSTANCE_COMMANDS} byte sizes
 * are computed analytically from the actual {@link BufferedCommand}/{@code DbValue} serialized
 * lengths, not read back from RocksDB (see scenario 1's approxWrittenBatchBytes for the same
 * technique) — {@code EngineRule} doesn't expose the underlying {@code ZeebeDb} handle needed for
 * real CF-size properties (only {@code ProcessingDbState} holds it, privately).
 *
 * <p>One thing this profile sweep settles: {@link ProcessInstanceRecord} — what actually gets
 * wrapped into a {@code BufferedCommand} — has no variables field at all (variables live in a
 * separate state/CF, populated via {@code VariableDocument} commands, which aren't in {@code
 * BpmnStreamProcessor}'s {@code BUFFERABLE_ON_SUSPEND} set). So however many/large an instance's
 * variables are, the buffer CF this POC adds is completely unaffected — the {@code
 * large-variables-instance} profile below demonstrates this directly by attaching a large variable
 * document and showing the buffered-command byte size doesn't move, then reports the variable
 * record's own size for contrast.
 */
public final class ProcessInstanceSuspendStorageGrowthBenchmark {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";
  private static final int BUFFERED_COMMANDS_PER_INSTANCE = 10;
  private static final int INSTANCE_COUNT = 1_000;

  private static final int SUSPENDED_KEY_BYTES = new DbLong().getLength();
  private static final int SUSPENDED_VALUE_BYTES = DbNil.INSTANCE.getLength();
  private static final int BUFFERED_KEY_BYTES = 2 * new DbLong().getLength();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Before
  public void setUp() {
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(3).toMillis());
  }

  @Test
  public void sweepCommandProfiles() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    final List<Profile> profiles =
        List.of(
            new Profile("activate-shallow", ProcessInstanceIntent.ACTIVATE_ELEMENT, r -> {}, false),
            new Profile(
                "complete-shallow (original scenario-3 shape)",
                ProcessInstanceIntent.COMPLETE_ELEMENT,
                r -> {},
                false),
            new Profile(
                "execution-listener",
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER,
                r -> r.setBpmnEventType(BpmnEventType.TIMER),
                false),
            new Profile(
                "deep-nested-path (8-level embedded subprocess/multi-instance)",
                ProcessInstanceIntent.COMPLETE_ELEMENT,
                r ->
                    r.setElementInstancePath(List.of(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)))
                        .setProcessDefinitionPath(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L))
                        .setCallingElementPath(List.of(1, 2, 3, 4, 5, 6, 7, 8)),
                false),
            new Profile(
                "long-tags-and-business-id (10 tags + 128-char id)",
                ProcessInstanceIntent.COMPLETE_ELEMENT,
                r ->
                    r.setTags(
                            java.util.stream.IntStream.range(0, 10)
                                .mapToObj(i -> "tag-" + i + "-abcdefghijklmno")
                                .collect(java.util.stream.Collectors.toSet()))
                        .setBusinessId("b".repeat(128)),
                false),
            new Profile(
                "large-variables-instance (20 vars x ~1KB each, attached before suspend)",
                ProcessInstanceIntent.COMPLETE_ELEMENT,
                r -> {},
                true));

    System.out.println(
        "\nprofile,cumulativeWallClockMillis,bufferedEntryBytes,suspendedCfBytes,bufferedCfBytes,totalBytes,bytesPerInstance");
    for (final Profile profile : profiles) {
      final int bufferedEntryBytes = BUFFERED_KEY_BYTES + sampleBufferedCommandValueBytes(profile);

      final long stepStartNanos = System.nanoTime();
      for (int i = 0; i < INSTANCE_COUNT; i++) {
        suspendWithBufferedCommands(profile);
      }
      final long stepMillis = Duration.ofNanos(System.nanoTime() - stepStartNanos).toMillis();

      final long suspendedCfBytes =
          (long) INSTANCE_COUNT * (SUSPENDED_KEY_BYTES + SUSPENDED_VALUE_BYTES);
      final long bufferedCfBytes =
          (long) INSTANCE_COUNT * BUFFERED_COMMANDS_PER_INSTANCE * bufferedEntryBytes;
      final long totalBytes = suspendedCfBytes + bufferedCfBytes;
      System.out.printf(
          "%s,%d,%d,%d,%d,%d,%d%n",
          profile.name,
          stepMillis,
          bufferedEntryBytes,
          suspendedCfBytes,
          bufferedCfBytes,
          totalBytes,
          totalBytes / INSTANCE_COUNT);

      if (profile.attachLargeVariables) {
        final int variableRecordBytes = sampleLargeVariableRecordBytes();
        System.out.println(
            "# for comparison only, NOT part of the buffer CFs above: one such variable's own"
                + " record size is "
                + variableRecordBytes
                + " bytes (x however many variables the instance has) — lives in a separate"
                + " state, untouched by suspend/buffer");
      }
    }
  }

  private void suspendWithBufferedCommands(final Profile profile) {
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    if (profile.attachLargeVariables) {
      final Map<String, Object> variables = new java.util.HashMap<>();
      for (int v = 0; v < 20; v++) {
        variables.put("var" + v, "x".repeat(1024));
      }
      engine.variables().ofScope(processInstanceKey).withDocument(variables).update();
    }

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    final ProcessInstanceRecordValue taskActivatedValue = taskActivated.getValue();
    final RecordToWrite[] commands = new RecordToWrite[BUFFERED_COMMANDS_PER_INSTANCE];
    for (int i = 0; i < BUFFERED_COMMANDS_PER_INSTANCE; i++) {
      commands[i] =
          RecordToWrite.command()
              .processInstance(
                  profile.intent, buildRecord(processInstanceKey, taskActivatedValue, profile))
              .key(taskActivated.getKey());
    }
    engine.writeRecords(commands);
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMMAND_BUFFERED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(BUFFERED_COMMANDS_PER_INSTANCE)
        .asList();
  }

  private ProcessInstanceRecord buildRecord(
      final long processInstanceKey,
      final ProcessInstanceRecordValue taskActivatedValue,
      final Profile profile) {
    final ProcessInstanceRecord record =
        new ProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(taskActivatedValue.getProcessDefinitionKey())
            .setElementId("task")
            .setFlowScopeKey(taskActivatedValue.getFlowScopeKey())
            .setBpmnProcessId(PROCESS_ID)
            .setVersion(taskActivatedValue.getVersion());
    profile.customizer.accept(record);
    return record;
  }

  /** One representative {@code BufferedCommand} entry's serialized length, for the byte math. */
  private int sampleBufferedCommandValueBytes(final Profile profile) {
    final ProcessInstanceRecord record =
        new ProcessInstanceRecord()
            .setBpmnElementType(BpmnElementType.SERVICE_TASK)
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(1L)
            .setElementId("task")
            .setFlowScopeKey(1L)
            .setBpmnProcessId(PROCESS_ID)
            .setVersion(1)
            .setBufferedOriginalKey(1L)
            .setBufferedElementIntent(profile.intent.value());
    profile.customizer.accept(record);
    return new BufferedCommand().setRecord(record).getLength();
  }

  /** One ~1KB variable's own record size, for the large-variables comparison profile. */
  private int sampleLargeVariableRecordBytes() {
    final VariableRecord record =
        new VariableRecord()
            .setName(io.camunda.zeebe.util.buffer.BufferUtil.wrapString("var0"))
            .setValue(
                io.camunda.zeebe.util.buffer.BufferUtil.wrapString("\"" + "x".repeat(1024) + "\""))
            .setScopeKey(1L)
            .setProcessInstanceKey(1L)
            .setProcessDefinitionKey(1L)
            .setBpmnProcessId(io.camunda.zeebe.util.buffer.BufferUtil.wrapString(PROCESS_ID))
            .setRootProcessInstanceKey(1L);
    return record.getLength();
  }

  private record Profile(
      String name,
      ProcessInstanceIntent intent,
      Consumer<ProcessInstanceRecord> customizer,
      boolean attachLargeVariables) {}
}
