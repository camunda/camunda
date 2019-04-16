/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.exporter.debug.DebugLogExporter;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ExporterManagerTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().done();

  private ExporterCfg exporterCfg;

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(
          brokerCfg -> {
            exporterCfg = new ExporterCfg();
            exporterCfg.setClassName(TestExporter.class.getName());
            exporterCfg.setId("test-exporter");

            brokerCfg.setExporters(Collections.singletonList(exporterCfg));
          });

  public ClientApiRule clientRule = new ClientApiRule(brokerRule::getAtomix);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = clientRule.partitionClient();

    TestExporter.records.clear();
  }

  @Test
  public void shouldRestoreExporterFromState() {

    // given
    final long deploymentKey1 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey1));

    // when
    TestExporter.records.clear();
    brokerRule.restartBroker();

    // then
    final long deploymentKey2 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey2));

    assertThat(TestExporter.records).extracting(r -> r.getKey()).doesNotContain(deploymentKey1);
  }

  @Test
  public void shouldRemoveExporterFromState() {

    // given
    final long deploymentKey1 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey1));

    // when
    brokerRule.getBrokerCfg().setExporters(Collections.emptyList());
    brokerRule.restartBroker();

    TestExporter.records.clear();
    brokerRule.getBrokerCfg().setExporters(Collections.singletonList(exporterCfg));
    brokerRule.restartBroker();

    // then
    final long deploymentKey2 = testClient.deploy(WORKFLOW);
    waitUntil(() -> isDeploymentExported(deploymentKey2));

    assertThat(TestExporter.records)
        .extracting(r -> r.getKey())
        .contains(deploymentKey1, deploymentKey2);
  }

  private boolean isDeploymentExported(long deploymentKey1) {
    return TestExporter.records.stream()
        .anyMatch(
            r ->
                r.getKey() == deploymentKey1
                    && r.getMetadata().getIntent() == DeploymentIntent.DISTRIBUTED);
  }

  public static class TestExporter extends DebugLogExporter {

    public static List<Record> records = new CopyOnWriteArrayList<>();

    private Controller controller;

    @Override
    public void open(Controller controller) {
      this.controller = controller;
    }

    @Override
    public void export(final Record record) {
      controller.updateLastExportedRecordPosition(record.getPosition());

      records.add(record);
    }
  }
}
