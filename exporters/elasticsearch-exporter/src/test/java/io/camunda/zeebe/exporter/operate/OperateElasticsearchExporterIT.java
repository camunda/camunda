/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.zeebe.exporter.TestClient;
import io.camunda.zeebe.exporter.TestSupport;
import io.camunda.zeebe.exporter.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.jackson.ZeebeProtocolModule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class OperateElasticsearchExporterIT {

  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private final OperateElasticsearchExporterConfiguration config =
      new OperateElasticsearchExporterConfiguration();
  private final ProtocolFactory factory = new ProtocolFactory();

  private OperateElasticsearchExporter exporter;
  private ExportBatchWriter writer;
  private ExporterTestController controller;
  private TestClient testClient;

  private RestHighLevelClient esClient;
  private OperateElasticsearchManager schemaManager;

  @BeforeAll
  public void beforeAll() {
    config.url = CONTAINER.getHttpHostAddress();
    // TODO: who is creating the indexes? exporter or operate? how do we want to do it in the test
    // then?
    // config.index.setNumberOfShards(1);
    // config.index.setNumberOfReplicas(1);
    // config.index.createTemplate = true;
    config.getBulk().size = 1; // force flushing on the first record

    // TODO: index router also depends on where and how we create the indexes
    // testClient = new TestClient(config, indexRouter);

  }

  @BeforeEach
  public void setUpExporter() throws Exception {
    controller = new ExporterTestController();

    exporter = new OperateElasticsearchExporter();

    exporter.configure(
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config)));
    exporter.open(controller);
    writer = exporter.getWriter();

    esClient = exporter.createEsClient();
    schemaManager = exporter.getSchemaManager();
  }

  @AfterEach
  public void tearDownExporter() {
    exporter.close();
    exporter = null;
    controller = null;
    writer = null;
  }

  @ParameterizedTest(name = "{0} - {1}")
  @MethodSource(
      "io.camunda.zeebe.exporter.operate.OperateElasticsearchExporterIT#provideParameters")
  public void shouldExportRecord(ValueType valueType, Intent intent) {

    // given
    Record<RecordValue> record =
        factory.generateRecord(
            valueType,
            b -> {
              b.withIntent(intent)
                  .withRecordType(RecordType.EVENT)
                  .withTimestamp(System.currentTimeMillis());

              return b;
            });
    if (valueType.equals(ValueType.JOB)) {
      final JobRecordValue recordValue = (JobRecordValue) record.getValue();
      record =
          factory.generateRecord(
              valueType,
              b ->
                  b.withValue(
                          ImmutableJobRecordValue.builder()
                              .from(recordValue)
                              .withDeadline(System.currentTimeMillis())
                              .build())
                      .withIntent(intent)
                      .withRecordType(RecordType.EVENT)
                      .withTimestamp(System.currentTimeMillis()));
    }

    // when
    exporter.export(record);

    // then
    if (valueType.equals(ValueType.DECISION_EVALUATION)) {
      final List<EvaluatedDecisionValue> evaluatedDecisions =
          ((DecisionEvaluationRecordValue) record.getValue()).getEvaluatedDecisions();
      for (int i = 1; i <= evaluatedDecisions.size(); i++) {
        final String expectedId = String.format("%d-%d", record.getKey(), i);
        final String indexName =
            schemaManager
                .getTemplateDescriptor(DecisionInstanceTemplate.class)
                .getFullQualifiedName();
        assertDocument(expectedId, indexName, "DecisionInstanceHandler");
      }
    } else {
      final List<ExportHandler<?, ?>> handlersForRecord =
          writer.getHandlersForValueType(record.getValueType());

      assertThat(handlersForRecord).isNotEmpty();

      for (ExportHandler<?, ?> handler : handlersForRecord) {

        if (handler.handlesRecord((Record) record)) {

          final String expectedId = handler.generateId((Record) record);
          final String indexName = handler.getIndexName();

          assertDocument(expectedId, indexName, handler.getClass().getSimpleName());
        }
      }
    }
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
    exporter.flush();

    // then
    // there should be a process instance record in the list view index
    final String listViewIndexName =
        schemaManager.getTemplateDescriptor(ListViewTemplate.class).getFullQualifiedName();
    final String processInstanceKey = "2251799813685251";
    final Map<String, Object> processInstance =
        findElasticsearchDocument(listViewIndexName, processInstanceKey);
    assertThat(processInstance)
        .contains(
            entry("id", "2251799813685251"),
            entry("key", 2251799813685251L),
            entry("partitionId", 0),
            entry("processDefinitionKey", 2251799813685249L),
            entry("bpmnProcessId", "process"),
            entry("processName", null),
            entry("processVersion", 1),
            entry("processInstanceKey", 2251799813685251L),
            entry("parentProcessInstanceKey", null),
            entry("parentFlowNodeInstanceKey", null),
            entry("startDate", "2023-12-12T08:55:35.547Z"),
            entry("endDate", "2023-12-12T08:55:35.671Z"),
            entry("state", "COMPLETED"),
            entry("batchOperationIds", null),
            entry("incident", false),
            entry("tenantId", "<default>"),
            entry("treePath", null));

    // there should be flow node instances
    final String flowNodeIndexName =
        schemaManager.getTemplateDescriptor(FlowNodeInstanceTemplate.class).getFullQualifiedName();

    final Map<String, FlowNodeInstanceEntity> flowNodeInstances =
        getMatchingDocuments(
            flowNodeIndexName,
            FlowNodeInstanceEntity.class,
            QueryBuilders.termQuery("processInstanceKey", 2251799813685251L));
    assertThat(flowNodeInstances).hasSize(3);

    // start event 2251799813685253
    final FlowNodeInstanceEntity startEvent = flowNodeInstances.get("2251799813685253");

    assertThat(startEvent.getId()).isEqualTo("2251799813685253");
    assertThat(startEvent.getKey()).isEqualTo(2251799813685253L);
    assertThat(startEvent.getPartitionId()).isEqualTo(0);
    assertThat(startEvent.getPosition()).isEqualTo(10);

    assertThat(startEvent.getFlowNodeId()).isEqualTo("start");
    assertThat(startEvent.getLevel()).isEqualTo(0);
    assertThat(startEvent.getType()).isEqualTo(FlowNodeType.START_EVENT);

    assertThat(startEvent.getBpmnProcessId()).isEqualTo("process");
    assertThat(startEvent.getProcessDefinitionKey()).isEqualTo(2251799813685249L);
    assertThat(startEvent.getProcessInstanceKey()).isEqualTo(2251799813685251L);

    assertThat(startEvent.getStartDate()).isEqualTo(asDate(1702371335547L));
    assertThat(startEvent.getEndDate()).isEqualTo(asDate(1702371335547L));
    assertThat(startEvent.getIncidentKey()).isNull();
    assertThat(startEvent.getTenantId()).isEqualTo("<default>");
    assertThat(startEvent.getTreePath()).isNull();

    // service task 2251799813685255
    final FlowNodeInstanceEntity serviceTask = flowNodeInstances.get("2251799813685255");

    assertThat(serviceTask.getId()).isEqualTo("2251799813685255");
    assertThat(serviceTask.getKey()).isEqualTo(2251799813685255L);
    assertThat(serviceTask.getPartitionId()).isEqualTo(0);
    assertThat(serviceTask.getPosition()).isEqualTo(17);

    assertThat(serviceTask.getFlowNodeId()).isEqualTo("task");
    assertThat(serviceTask.getLevel()).isEqualTo(0);
    assertThat(serviceTask.getType()).isEqualTo(FlowNodeType.SERVICE_TASK);

    assertThat(serviceTask.getBpmnProcessId()).isEqualTo("process");
    assertThat(serviceTask.getProcessDefinitionKey()).isEqualTo(2251799813685249L);
    assertThat(serviceTask.getProcessInstanceKey()).isEqualTo(2251799813685251L);

    assertThat(serviceTask.getStartDate()).isEqualTo(asDate(1702371335547L));
    assertThat(serviceTask.getEndDate()).isEqualTo(asDate(1702371335671L));
    assertThat(serviceTask.getIncidentKey()).isNull();
    assertThat(serviceTask.getTenantId()).isEqualTo("<default>");
    assertThat(serviceTask.getTreePath()).isNull();

    // end event 2251799813685260
    final FlowNodeInstanceEntity endEvent = flowNodeInstances.get("2251799813685260");

    assertThat(endEvent.getId()).isEqualTo("2251799813685260");
    assertThat(endEvent.getKey()).isEqualTo(2251799813685260L);
    assertThat(endEvent.getPartitionId()).isEqualTo(0);
    assertThat(endEvent.getPosition()).isEqualTo(30);

    assertThat(endEvent.getFlowNodeId()).isEqualTo("end");
    assertThat(endEvent.getLevel()).isEqualTo(0);
    assertThat(endEvent.getType()).isEqualTo(FlowNodeType.END_EVENT);

    assertThat(endEvent.getBpmnProcessId()).isEqualTo("process");
    assertThat(endEvent.getProcessDefinitionKey()).isEqualTo(2251799813685249L);
    assertThat(endEvent.getProcessInstanceKey()).isEqualTo(2251799813685251L);

    assertThat(endEvent.getStartDate()).isEqualTo(asDate(1702371335671L));
    assertThat(endEvent.getEndDate()).isEqualTo(asDate(1702371335671L));
    assertThat(endEvent.getIncidentKey()).isNull();
    assertThat(endEvent.getTenantId()).isEqualTo("<default>");
    assertThat(endEvent.getTreePath()).isNull();
  }

  @Test
  public void shouldExportTreePathsForCallActivies() throws PersistenceException {

    // given
    // effectively disabling automatic flush so that we can flush all changes at once and at will
    exporter.setBatchSize(1000);

    final List<Record<?>> records = readRecordsFromJsonResource("call-activity-event-log.json");

    // when
    records.forEach(record -> exporter.export(record));
    exporter.flush();

    // then
    // there should be a process instance record in the list view index
    final String listViewIndexName =
        schemaManager.getTemplateDescriptor(ListViewTemplate.class).getFullQualifiedName();
    final String parentProcessInstanceKey = "2251799813685252";
    final String childProcessInstanceKey = "2251799813685257";
    final Map<String, Object> parentProcessInstance =
        findElasticsearchDocument(listViewIndexName, parentProcessInstanceKey);
    final Map<String, Object> childProcessInstance =
        findElasticsearchDocument(listViewIndexName, childProcessInstanceKey);
    assertThat(parentProcessInstance)
        .contains(
            entry("id", parentProcessInstanceKey),
            entry("treePath", "PI_2251799813685252"));
    assertThat(childProcessInstance)
        .contains(
            entry("id", childProcessInstanceKey),
            entry("treePath", "PI_2251799813685252/FN_call/FNI_2251799813685256/PI_2251799813685257"));
  }

  @Test
  public void shouldExportTreePathsSubprocesses() throws PersistenceException {

    // given
    // effectively disabling automatic flush so that we can flush all changes at once and at will
    exporter.setBatchSize(1000);

    final List<Record<?>> records = readRecordsFromJsonResource("subprocess-event-log.json");

    // when
    records.forEach(record -> exporter.export(record));
    exporter.flush();

    // then
    // there should be a process instance record in the list view index
    final String flowNodeIndexName =
        schemaManager.getTemplateDescriptor(FlowNodeInstanceTemplate.class).getFullQualifiedName();

    final Map<String, FlowNodeInstanceEntity> flowNodeInstances =
        getMatchingDocuments(
            flowNodeIndexName,
            FlowNodeInstanceEntity.class,
            QueryBuilders.termQuery("level", "2"));
    int i = 1;
    assertThat(flowNodeInstances).hasSize(2);

    final FlowNodeInstanceEntity startEvent = flowNodeInstances.get("2251799813685256");
    assertThat(startEvent.getTreePath()).isEqualTo("2251799813685251/2251799813685255/2251799813685256");

    final FlowNodeInstanceEntity endEvent = flowNodeInstances.get("2251799813685258");
    assertThat(endEvent.getTreePath()).isEqualTo("2251799813685251/2251799813685255/2251799813685258");

  }


  private OffsetDateTime asDate(long time) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
  }

  private List<Record<?>> readRecordsFromJsonResource(String resourceName) {
    final List<Record<?>> result = new ArrayList<>();

    try {

      final ObjectMapper objectMapper =
          new ObjectMapper().registerModule(new ZeebeProtocolModule());
      try (InputStream inputStream =
          OperateElasticsearchExporterIT.class.getClassLoader().getResourceAsStream(resourceName)) {
        final List<String> lines = IOUtils.readLines(inputStream, StandardCharsets.UTF_8);

        for (String jsonString : lines) {
          final Record<?> record =
              objectMapper.readValue(jsonString, new TypeReference<Record<?>>() {});
          result.add(record);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(
          String.format("Could not read records from classpath resource %s", resourceName), e);
    }

    return result;
  }

  private void assertDocument(final String expectedId, final String indexName, String handlerName) {
    final Map<String, Object> document = findElasticsearchDocument(indexName, expectedId);
    assertThat(document).isNotNull();
    System.out.println(String.format("Returned document %s", document));
  }

  private Map<String, Object> findElasticsearchDocument(String index, String id) {

    try {
      schemaManager.refresh(index); // ensure latest data is visible
      final GetRequest request = new GetRequest(index).id(id);
      final GetResponse response = esClient.get(request, RequestOptions.DEFAULT);
      if (response.isExists()) {
        return response.getSourceAsMap();
      } else {
        throw new RuntimeException(
            String.format("Could not find document with id %s in index %s", id, index));
      }

    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private <T extends OperateEntity<T>> Map<String, T> getMatchingDocuments(
      String index, Class<T> entityClass, QueryBuilder query) {
    try {
      schemaManager.refresh(index); // ensure latest data is visible

      final SearchRequest request =
          new SearchRequest(index).source(new SearchSourceBuilder().query(query));
      final List<T> searchResults =
          ElasticsearchUtil.scroll(
              request, entityClass, NoSpringJacksonConfig.buildObjectMapper(), esClient);

      final Map<String, T> result = new HashMap<>();
      searchResults.forEach(entity -> result.put(entity.getId(), entity));

      return result;

    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of(ValueType.DECISION, DecisionIntent.CREATED),
        Arguments.of(ValueType.DECISION_REQUIREMENTS, DecisionRequirementsIntent.CREATED),
        Arguments.of(ValueType.DECISION_EVALUATION, DecisionEvaluationIntent.FAILED),
        Arguments.of(ValueType.DECISION_EVALUATION, DecisionEvaluationIntent.EVALUATED),
        Arguments.of(ValueType.INCIDENT, IncidentIntent.CREATED, IncidentIntent.RESOLVED),
        Arguments.of(ValueType.PROCESS, ProcessIntent.CREATED),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETING),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATING),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATED),
        Arguments.of(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_MIGRATED),
        Arguments.of(ValueType.VARIABLE, VariableIntent.CREATED, VariableIntent.UPDATED),
        Arguments.of(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATED),
        Arguments.of(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionIntent.CREATED),
        Arguments.of(ValueType.JOB, JobIntent.CREATED),
        Arguments.of(ValueType.JOB, JobIntent.COMPLETED),
        Arguments.of(ValueType.JOB, JobIntent.CANCELED),
        Arguments.of(ValueType.JOB, JobIntent.TIMED_OUT),
        Arguments.of(ValueType.JOB, JobIntent.FAILED),
        Arguments.of(ValueType.JOB, JobIntent.RETRIES_UPDATED),
        Arguments.of(ValueType.JOB, JobIntent.ERROR_THROWN),
        Arguments.of(ValueType.JOB, JobIntent.RECURRED_AFTER_BACKOFF),
        Arguments.of(ValueType.JOB, JobIntent.YIELDED),
        Arguments.of(ValueType.JOB, JobIntent.TIMEOUT_UPDATED),
        Arguments.of(ValueType.JOB, JobIntent.MIGRATED));
  }
}
