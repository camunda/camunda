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

import static org.assertj.core.api.Assertions.assertThat;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.util.ElasticsearchForkedJvm;
import io.zeebe.exporter.util.ElasticsearchNode;
import io.zeebe.test.exporter.ExporterIntegrationRule;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;

@RunWith(Parameterized.class)
public class ElasticsearchExporterIT {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ExporterIntegrationRule exporterBrokerRule = new ExporterIntegrationRule();

  private ElasticsearchNode elastic;
  private ElasticsearchExporterConfiguration configuration;
  private ElasticsearchTestClient esClient;

  @Parameter(0)
  public String name;

  @Parameter(1)
  public Consumer<ElasticsearchNode> elasticConfigurator;

  @Parameter(2)
  public Consumer<ElasticsearchExporterConfiguration> exporterConfigurator;

  @Parameters(name = "{0}")
  public static Object[][] data() {
    return new Object[][] {
      new Object[] {
        "defaults", elastic(c -> {}), exporter(c -> {}),
      },
      new Object[] {
        "basic authentication",
        elastic(c -> c.withUser("zeebe", "1234567")),
        exporter(
            c -> {
              c.authentication.username = "zeebe";
              c.authentication.password = "1234567";
            })
      },
      new Object[] {
        "one way ssl handshake",
        elastic(c -> c.withKeyStore("certs/elastic-certificates.p12")),
        exporter(c -> {})
      }
    };
  }

  @Before
  public void setUp() {
    elastic = new ElasticsearchForkedJvm(temporaryFolder);
  }

  @After
  public void tearDown() throws IOException {
    if (esClient != null) {
      esClient.close();
      esClient = null;
    }

    exporterBrokerRule.stop();
    elastic.stop();
    configuration = null;
    RecordingExporter.reset();
  }

  @Test
  public void shouldExportRecords() {
    // given
    elasticConfigurator.accept(elastic);
    elastic.start();

    // given
    configuration = getDefaultConfiguration();
    exporterConfigurator.accept(configuration);

    // when
    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);
    exporterBrokerRule.start();
    exporterBrokerRule.performSampleWorkload();

    // then

    // assert index settings for all created indices
    esClient = createElasticsearchClient(configuration);
    assertIndexSettings();

    // assert all records which where recorded during the tests where exported
    exporterBrokerRule.visitExportedRecords(
        r -> {
          if (configuration.shouldIndexRecord(r)) {
            assertRecordExported(r);
          }
        });
  }

  private void assertIndexSettings() {
    final ImmutableOpenMap<String, Settings> settingsForIndices = esClient.getSettingsForIndices();
    for (ObjectCursor<String> key : settingsForIndices.keys()) {
      final Settings settings = settingsForIndices.get(key.value);
      final Integer numberOfShards = settings.getAsInt("index.number_of_shards", -1);
      final Integer numberOfReplicas = settings.getAsInt("index.number_of_replicas", -1);

      assertThat(numberOfShards)
          .withFailMessage(
              "Expected number of shards of index %s to be 1 but was %d", key.value, numberOfShards)
          .isEqualTo(1);
      assertThat(numberOfReplicas)
          .withFailMessage(
              "Expected number of replicas of index %s to be 0 but was %d",
              key.value, numberOfReplicas)
          .isEqualTo(0);
    }
  }

  private void assertRecordExported(Record<?> record) {
    final Map<String, Object> source = esClient.get(record);
    assertThat(source)
        .withFailMessage("Failed to fetch record %s from elasticsearch", record)
        .isNotNull();

    assertThat(source).isEqualTo(recordToMap(record));
  }

  protected ElasticsearchTestClient createElasticsearchClient(
      ElasticsearchExporterConfiguration configuration) {
    return new ElasticsearchTestClient(
        configuration, new ZbLogger("io.zeebe.exporter.elasticsearch"));
  }

  private Map<String, Object> recordToMap(final Record<?> record) {
    final JsonNode jsonNode;
    try {
      jsonNode = MAPPER.readTree(record.toJson());
    } catch (IOException e) {
      throw new AssertionError("Failed to deserialize json of record " + record.toJson(), e);
    }

    return MAPPER.convertValue(jsonNode, Map.class);
  }

  private ElasticsearchExporterConfiguration getDefaultConfiguration() {
    final ElasticsearchExporterConfiguration configuration =
        new ElasticsearchExporterConfiguration();

    configuration.url = elastic.getRestHttpHost().toString();

    configuration.bulk.delay = 1;
    configuration.bulk.size = 1;

    configuration.index.prefix = "test-record";
    configuration.index.createTemplate = true;
    configuration.index.command = true;
    configuration.index.event = true;
    configuration.index.rejection = true;
    configuration.index.deployment = true;
    configuration.index.incident = true;
    configuration.index.job = true;
    configuration.index.jobBatch = true;
    configuration.index.message = true;
    configuration.index.messageSubscription = true;
    configuration.index.raft = true;
    configuration.index.variable = true;
    configuration.index.workflowInstance = true;
    configuration.index.workflowInstanceSubscription = true;

    return configuration;
  }

  public static class ElasticsearchTestClient extends ElasticsearchClient {

    public ElasticsearchTestClient(ElasticsearchExporterConfiguration configuration, Logger log) {
      super(configuration, log);
    }

    protected ImmutableOpenMap<String, Settings> getSettingsForIndices() {
      final GetSettingsRequest settingsRequest = new GetSettingsRequest();
      try {
        return client
            .indices()
            .getSettings(settingsRequest, RequestOptions.DEFAULT)
            .getIndexToSettings();
      } catch (IOException e) {
        throw new ElasticsearchExporterException("Failed to get index settings", e);
      }
    }

    protected Map<String, Object> get(Record<?> record) {
      final GetRequest request = new GetRequest(indexFor(record), typeFor(record), idFor(record));
      try {
        final GetResponse response = client.get(request, RequestOptions.DEFAULT);
        if (response.isExists()) {
          return response.getSourceAsMap();
        } else {
          return null;
        }
      } catch (IOException e) {
        throw new ElasticsearchExporterException(
            "Failed to get record " + idFor(record) + " from index " + indexFor(record));
      }
    }
  }

  private static Consumer<ElasticsearchNode> elastic(Consumer<ElasticsearchNode> configurator) {
    return configurator;
  }

  private static Consumer<ElasticsearchExporterConfiguration> exporter(
      Consumer<ElasticsearchExporterConfiguration> configurator) {
    return configurator;
  }
}
