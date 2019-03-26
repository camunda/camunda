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

import io.zeebe.exporter.util.ElasticsearchNode;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ElasticsearchExporterIT extends AbstractElasticsearchExporterIntegrationTestCase {
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

  private static Consumer<ElasticsearchNode> elastic(Consumer<ElasticsearchNode> configurator) {
    return configurator;
  }

  private static Consumer<ElasticsearchExporterConfiguration> exporter(
      Consumer<ElasticsearchExporterConfiguration> configurator) {
    return configurator;
  }
}
