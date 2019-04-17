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

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.exporter.debug.DebugLogExporter;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.exporter.api.context.Controller;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
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

  public ClientApiRule clientRule = new ClientApiRule(brokerRule::getAtomix);
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
      final RecordMetadata metadata = record.getMetadata();
      if (metadata.getValueType() == ValueType.JOB && metadata.getIntent() == JobIntent.CREATED) {
        exportLatch.countDown();
      }

      super.export(record);
    }
  }
}
