/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.TestSupport;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import io.camunda.operate.zeebeimport.elasticsearch.ElasticsearchRecordsReader;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FinishedImportingIT extends OperateZeebeAbstractIT {
  private static final ElasticsearchExporter exporter = new ElasticsearchExporter();
  private static final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  @Autowired public SchemaManager schemaManager;
  @Autowired protected ZeebeImporter zeebeImporter;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private OperateProperties operateProperties;
  private final ProtocolFactory factory = new ProtocolFactory();

  @Before
  public void beforeEach() {
    config.index.prefix = operateProperties.getZeebeElasticsearch().getPrefix();
    config.index.setNumberOfShards(1);
    config.index.setNumberOfReplicas(0);
    config.index.createTemplate = true;
    config.retention.setEnabled(false);
    config.bulk.size = 1; // force flushing on the first record

    // here enable all indexes that needed during the tests beforehand as they will be created once
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(config.index, valueType, true));

    final var exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
    exporter.configure(exporterTestContext);
    exporter.open(new ExporterTestController());
  }

  @Test
  public void shouldMarkRecordReadersAsCompletedIf8_7_0RecordReceived() throws IOException {
    // given
    final var record = generateProcessInstanceRecord("8.6.0", 1);
    exporter.export(record);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    zeebeImporter.performOneRoundOfImport();

    // when
    final var record2 = generateProcessInstanceRecord("8.7.0", 1);
    exporter.export(record2);
    esClient.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);

    // receives 8.7 record and marks partition as finished importing
    zeebeImporter.performOneRoundOfImport();

    // then
    // require two checks to avoid race condition. If records are written to zeebe indices and
    // before a refresh, the record reader pulls the import batch is empty so it then says that the
    // record reader is done when it is not.
    zeebeImporter.performOneRoundOfImport();
    zeebeImporter.performOneRoundOfImport();

    final var completedRecordReaders = getRecordReadersCompletionStatus();
    Assertions.assertThat(completedRecordReaders.entrySet().stream())
        .filteredOn(e -> e.getKey().split("-")[1].equals("1"))
        .allMatch(e -> Boolean.parseBoolean(e.getValue().toString()));

    Assertions.assertThat(completedRecordReaders.entrySet().stream())
        .filteredOn(e -> e.getKey().split("-")[1].equals("2"))
        .allMatch(e -> !Boolean.parseBoolean(e.getValue().toString()));
  }

  private <T extends RecordValue> Record<T> generateProcessInstanceRecord(
      final String brokerVersion, final int partitionId) {
    return factory.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withBrokerVersion(brokerVersion)
                .withPartitionId(partitionId)
                .withTimestamp(System.currentTimeMillis()));
  }

  private Map<String, Boolean> getRecordReadersCompletionStatus() throws IOException {
    return esClient
        .get(
            new GetRequest(ElasticsearchRecordsReader.COMPLETED_RECORD_READER_INDEX)
                .id(ElasticsearchRecordsReader.COMPLETED_RECORD_READER_DOCUMENT_ID)
                .refresh(true),
            RequestOptions.DEFAULT)
        .getSource()
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(Entry::getKey, e -> Boolean.parseBoolean(e.getValue().toString())));
  }
}
