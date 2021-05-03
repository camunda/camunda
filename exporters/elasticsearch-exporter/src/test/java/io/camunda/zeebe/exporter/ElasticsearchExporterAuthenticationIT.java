/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.exporter.util.ElasticsearchNode;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ElasticsearchExporterAuthenticationIT
    extends AbstractElasticsearchExporterIntegrationTestCase {
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
              c.getAuthentication().setUsername("zeebe");
              c.getAuthentication().setPassword("1234567");
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

  private static Consumer<ElasticsearchNode> elastic(
      final Consumer<ElasticsearchNode> configurator) {
    return configurator;
  }

  private static Consumer<ElasticsearchExporterConfiguration> exporter(
      final Consumer<ElasticsearchExporterConfiguration> configurator) {
    return configurator;
  }
}
