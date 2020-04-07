/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.exporter.debug.DebugLogExporter;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.msgpack.value.DocumentValue;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.zeebe.test.broker.protocol.commandapi.PartitionTestClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ExporterManagerTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().done();

  private static final String TEST_EXPORTER_ID = "test-exporter";

  private ExporterCfg exporterCfg;
  private final EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(
          brokerCfg -> {
            exporterCfg = new ExporterCfg();
            exporterCfg.setClassName(TestExporter.class.getName());

            brokerCfg.getExporters().put(TEST_EXPORTER_ID, exporterCfg);
          });
  public final CommandApiRule clientRule = new CommandApiRule(brokerRule::getAtomix);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);
  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = clientRule.partitionClient();

    TestExporter.RECORDS.clear();
  }

  @Test
  public void shouldRestoreExporterFromState() {

    // given
    final long deploymentKey1 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey1));

    // when
    TestExporter.RECORDS.clear();
    brokerRule.restartBroker();

    // then
    final long deploymentKey2 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey2));

    assertThat(TestExporter.RECORDS).extracting(Record::getKey).doesNotContain(deploymentKey1);
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/2810")
  public void shouldRemoveExporterFromState() {
    // given
    final long deploymentKey1 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey1));

    // when
    brokerRule.getBrokerCfg().getExporters().remove(exporterCfg);
    brokerRule.restartBroker();

    // TODO: remove workaround to force new snapshot by publishing new record
    // (https://github.com/zeebe-io/zeebe/issues/2490)
    testClient.publishMessage("msg", "123", DocumentValue.EMPTY_DOCUMENT).getKey();

    TestExporter.RECORDS.clear();
    brokerRule.getBrokerCfg().getExporters().put(TEST_EXPORTER_ID, exporterCfg);
    brokerRule.restartBroker();

    // then
    final long deploymentKey2 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey2));

    assertThat(TestExporter.RECORDS)
        .extracting(Record::getKey)
        .contains(deploymentKey1, deploymentKey2);
  }

  private boolean isDeploymentExported(final long deploymentKey1) {
    return TestExporter.RECORDS.stream()
        .anyMatch(
            r -> r.getKey() == deploymentKey1 && r.getIntent() == DeploymentIntent.DISTRIBUTED);
  }

  public static class TestExporter extends DebugLogExporter {

    static final List<Record> RECORDS = new CopyOnWriteArrayList<>();

    private Controller controller;

    @Override
    public void open(final Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record record) {
      controller.updateLastExportedRecordPosition(record.getPosition());

      RECORDS.add(record.clone());
    }
  }
}
