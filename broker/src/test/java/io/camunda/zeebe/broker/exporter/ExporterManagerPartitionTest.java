/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.debug.DebugLogExporter;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ExporterManagerPartitionTest {

  private static final int PARTITIONS = 3;
  private static final String TEST_EXPORTER_ID = "test-exporter";

  public final EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(
          brokerCfg -> {
            brokerCfg.getCluster().setPartitionsCount(PARTITIONS);

            final ExporterCfg exporterCfg = new ExporterCfg();
            exporterCfg.setClassName(TestExporter.class.getName());

            brokerCfg.getExporters().put(TEST_EXPORTER_ID, exporterCfg);
          });

  public final CommandApiRule clientRule = new CommandApiRule(brokerRule::getAtomixCluster);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldRunExporterForEveryPartition() throws InterruptedException {
    // given
    IntStream.range(START_PARTITION_ID, START_PARTITION_ID + PARTITIONS).forEach(this::createJob);

    // then
    assertThat(TestExporter.CONFIGURE_LATCH.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(TestExporter.OPEN_LATCH.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(TestExporter.EXPORT_LATCH.await(5, TimeUnit.SECONDS)).isTrue();

    // when
    brokerRule.stopBroker();

    // then
    assertThat(TestExporter.CLOSE_LATCH.await(5, TimeUnit.SECONDS)).isTrue();
  }

  void createJob(final int partitionId) {
    clientRule.partitionClient(partitionId).createJob("test");
  }

  public static class TestExporter extends DebugLogExporter {

    // configure will be called initial for validation and after that for every partition
    static final CountDownLatch CONFIGURE_LATCH = new CountDownLatch(PARTITIONS + 1);
    static final CountDownLatch OPEN_LATCH = new CountDownLatch(PARTITIONS);
    static final CountDownLatch CLOSE_LATCH = new CountDownLatch(PARTITIONS);
    static final CountDownLatch EXPORT_LATCH = new CountDownLatch(PARTITIONS);

    @Override
    public void configure(final Context context) {
      CONFIGURE_LATCH.countDown();
      super.configure(context);
    }

    @Override
    public void open(final Controller controller) {
      OPEN_LATCH.countDown();
      super.open(controller);
    }

    @Override
    public void close() {
      CLOSE_LATCH.countDown();
      super.close();
    }

    @Override
    public void export(final Record<?> record) {
      if (record.getValueType() == ValueType.JOB && record.getIntent() == JobIntent.CREATED) {
        EXPORT_LATCH.countDown();
      }

      super.export(record);
    }
  }
}
