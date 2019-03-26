/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.exporter;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.elasticsearch.ElasticsearchStatusException;
import org.junit.Test;

public class ElasticsearchExporterFaultToleranceIT
    extends AbstractElasticsearchExporterIntegrationTestCase {

  @Test
  public void shouldExportEvenIfElasticNotInitiallyReachable() {
    // given
    configuration = getDefaultConfiguration();
    configuration.index.prefix = "zeebe";
    esClient = createElasticsearchClient(configuration);

    // when
    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);
    exporterBrokerRule.start();
    exporterBrokerRule.publishMessage("message", "123");
    elastic.start();

    // then
    RecordingExporter.messageRecords()
        .withCorrelationKey("123")
        .withName("message")
        .forEach(r -> TestUtil.waitUntil(() -> wasExported(r)));
    assertIndexSettings();
  }

  private boolean wasExported(Record<?> record) {
    try {
      return esClient.get(record) != null;
    } catch (ElasticsearchStatusException e) {
      // suppress exception in order to retry and see if it was exported yet or not
      // the exception can occur since elastic may not be ready yet, or maybe the index hasn't been
      // created yet, etc.
    }

    return false;
  }
}
