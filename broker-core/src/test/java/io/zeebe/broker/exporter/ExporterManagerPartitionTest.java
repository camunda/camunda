/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.exporter.debug.DebugLogExporter;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExporterManagerPartitionTest {

  private static final int PARTITIONS = 3;

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(
          brokerCfg -> {
            brokerCfg.getCluster().setPartitionsCount(PARTITIONS);

            final ExporterCfg exporterCfg = new ExporterCfg();
            exporterCfg.setClassName(TestExporter.class.getName());
            exporterCfg.setId("test-exporter");

            brokerCfg.getExporters().add(exporterCfg);
          });

  public CommandApiRule clientRule = new CommandApiRule(brokerRule::getAtomix);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Test
  public void shouldRunExporterForEveryPartition() throws InterruptedException {
    // given
    IntStream.range(START_PARTITION_ID, START_PARTITION_ID + PARTITIONS).forEach(this::createJob);

    // then
    assertThat(TestExporter.configureLatch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(TestExporter.openLatch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(TestExporter.exportLatch.await(5, TimeUnit.SECONDS)).isTrue();

    // when
    brokerRule.stopBroker();

    // then
    assertThat(TestExporter.closeLatch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  void createJob(final int partitionId) {
    clientRule.partitionClient(partitionId).createJob("test");
  }

  public static class TestExporter extends DebugLogExporter {

    // configure will be called initial for validation and after that for every partition
    static CountDownLatch configureLatch = new CountDownLatch(PARTITIONS + 1);
    static CountDownLatch openLatch = new CountDownLatch(PARTITIONS);
    static CountDownLatch closeLatch = new CountDownLatch(PARTITIONS);
    static CountDownLatch exportLatch = new CountDownLatch(PARTITIONS);

    @Override
    public void configure(final Context context) {
      configureLatch.countDown();
      super.configure(context);
    }

    @Override
    public void open(final Controller controller) {
      openLatch.countDown();
      super.open(controller);
    }

    @Override
    public void close() {
      closeLatch.countDown();
      super.close();
    }

    @Override
    public void export(final Record record) {
      if (record.getValueType() == ValueType.JOB && record.getIntent() == JobIntent.CREATED) {
        exportLatch.countDown();
      }

      super.export(record);
    }
  }
}
