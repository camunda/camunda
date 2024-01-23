/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.zeebe.exporter.ElasticsearchIndexManager;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;
import java.util.Map;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.Test;

public class DualExportIT extends AbstractOperateElasticsearchExporterIT {

  @Override
  protected void configureExporter(OperateElasticsearchExporterConfiguration configuration) {
    configuration.setWriteToZeebeIndexes(true);
  }

  @Test
  public void shouldExportAFullProcessInstanceLog()
      throws JsonProcessingException, PersistenceException {

    // given
    // effectively disabling automatic flush so that we can flush all changes at once and at will
    exporter.setBatchSize(1000);

    final List<Record<?>> records = readRecordsFromJsonResource("process-instance-event-log.json");

    // when
    records.forEach(record -> exporter.export(record));
    exporter.flushSync();

    // then
    // there should be the Operate entities
    final String listViewIndexName =
        schemaManager.getTemplateDescriptor(ListViewTemplate.class).getFullQualifiedName();
    final String processInstanceKey = "2251799813685251";
    final Map<String, Object> processInstance =
        findElasticsearchDocument(listViewIndexName, processInstanceKey);
    // details for all of the Operate entities are asserted in OperateElasticsearchExporterIT
    assertThat(processInstance).isNotNull();

    // there should be flow node instances
    final String flowNodeIndexName =
        schemaManager.getTemplateDescriptor(FlowNodeInstanceTemplate.class).getFullQualifiedName();

    final Map<String, FlowNodeInstanceEntity> flowNodeInstances =
        getMatchingDocuments(
            flowNodeIndexName,
            FlowNodeInstanceEntity.class,
            QueryBuilders.termQuery("processInstanceKey", 2251799813685251L));
    assertThat(flowNodeInstances).hasSize(3);

    // and there are Zeebe records
    final ElasticsearchIndexManager zeebeSchemaManager = exporter.getZeebeSchemaManager();
    final List<Map<String, Object>> processInstanceRecords =
        findElasticsearchDocuments(
            zeebeSchemaManager.getIndexForValueType(ValueType.PROCESS_INSTANCE, "2023-12-12"),
            QueryBuilders.matchAllQuery());
    assertThat(processInstanceRecords).hasSize(18);
    // TODO: can assert all the events here
  }
}
