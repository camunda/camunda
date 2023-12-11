package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.TestClient;
import io.camunda.zeebe.exporter.TestSupport;
import io.camunda.zeebe.exporter.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
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
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
            valueType, b -> {
              b.withIntent(intent).withRecordType(RecordType.EVENT)
                  .withTimestamp(System.currentTimeMillis());

              return b;
            });
    if (valueType.equals(ValueType.JOB)) {
      final JobRecordValue recordValue = (JobRecordValue) record.getValue();
      record =
          factory.generateRecord(
              valueType, b -> b.withValue(ImmutableJobRecordValue.builder().from(recordValue)
                  .withDeadline(System.currentTimeMillis()).build())
                  .withIntent(intent).withRecordType(RecordType.EVENT)
                  .withTimestamp(System.currentTimeMillis()));
    }

    // when
    exporter.export(record);

    // then
    if (valueType.equals(ValueType.DECISION_EVALUATION)) {
      final List<EvaluatedDecisionValue> evaluatedDecisions = ((DecisionEvaluationRecordValue) record.getValue()).getEvaluatedDecisions();
      for (int i = 1; i <= evaluatedDecisions.size(); i++) {
        String expectedId = String.format("%d-%d", record.getKey(), i);
        String indexName = schemaManager.getTemplateDescriptor(DecisionInstanceTemplate.class)
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

  private void assertDocument(final String expectedId, final String indexName, String handlerName) {
    final Map<String, Object> document =
        findElasticsearchDocument(indexName, expectedId, handlerName);
    assertThat(document).isNotNull();
    System.out.println(String.format("Returned document %s", document));
  }

  private Map<String, Object> findElasticsearchDocument(
      String index, String id, String handlerName) {

    try {
      schemaManager.refresh(index); // ensure latest data is visible
      final GetRequest request = new GetRequest(index).id(id);
      final GetResponse response = esClient.get(request, RequestOptions.DEFAULT);
      if (response.isExists()) {
        return response.getSourceAsMap();
      } else {
        throw new RuntimeException(
            String.format(
                "Could not find document with id %s in index %s",
                id, index, handlerName));
      }

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
      Arguments.of(ValueType.VARIABLE_DOCUMENT, Arrays.asList()),
      Arguments.of(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionIntent.CREATED),
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
      Arguments.of(ValueType.JOB, JobIntent.MIGRATED)
    );
  }

}
