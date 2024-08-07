/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.engine.property.Action.ExecuteScheduledTask;
import io.camunda.zeebe.engine.property.Action.ProcessRecord;
import io.camunda.zeebe.engine.property.Action.WriteRecord;
import io.camunda.zeebe.engine.property.InMemoryEngine;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.test.util.record.CompactRecordLogger;
import java.nio.charset.StandardCharsets;
import java.util.SequencedCollection;
import org.junit.jupiter.api.Test;

final class MessageCorrelationPropertyTest {

  @Test
  void test() {
    final var engines = InMemoryEngine.createEngines(3);
    final var engine = engines[0];

    final var process =
        Bpmn.createExecutableProcess("test").startEvent().manualTask().endEvent().done();

    final var deploymentMetadata = new RecordMetadata();
    deploymentMetadata
        .recordType(RecordType.COMMAND)
        .valueType(ValueType.DEPLOYMENT)
        .intent(DeploymentIntent.CREATE);
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResource(Bpmn.convertToString(process).getBytes(StandardCharsets.UTF_8))
        .setResourceName("process.xml");

    engine.runAction(new WriteRecord(1, LogAppendEntry.of(deploymentMetadata, deploymentRecord)));

    engine.runAction(new ProcessRecord(1, true));
    //    engine.runAction(
    //        new WriteRecord(
    //            1,
    //            RecordToWrite.command()
    //                .processInstanceCreation(
    //                    ProcessInstanceCreationIntent.CREATE,
    //                    new
    // ProcessInstanceCreationRecord().setBpmnProcessId("test").setVersion(1))));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ProcessRecord(1, true));
    engine.runAction(new ExecuteScheduledTask(1, true));

    new CompactRecordLogger(engines[0].records()).log();
    new CompactRecordLogger(engines[1].records()).log();
    new CompactRecordLogger(engines[2].records()).log();
  }

  static final class AllMessagesExpire implements MessageProperty {
    @Override
    public void verify(
        final Record<?> publishedMessage, final SequencedCollection<Record<?>> records) {
      final var messageKey = publishedMessage.getKey();
      final var expiry =
          records.stream()
              .filter(
                  r ->
                      r.getKey() == messageKey
                          && r.getValueType() == ValueType.MESSAGE
                          && r.getIntent() == MessageIntent.EXPIRED)
              .findAny();
      if (expiry.isEmpty()) {
        throw new AssertionError("Expected message with key %s to expire".formatted(messageKey));
      }
    }
  }

  interface MessageProperty {
    void verify(Record<?> publishedMessage, SequencedCollection<Record<?>> records);
  }
}
