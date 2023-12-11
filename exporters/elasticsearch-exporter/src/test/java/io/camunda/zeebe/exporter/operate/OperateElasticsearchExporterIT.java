package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebe.ZeebeESConstants;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.RestClientFactory;
import io.camunda.zeebe.exporter.TestClient;
import io.camunda.zeebe.exporter.TestSupport;
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
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.collection.Tuple;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class OperateElasticsearchExporterIT {


  private static final Map<ValueType, List<? extends Intent>> INTENTS_PER_VALUE_TYPE =
      new HashMap<>();

  static {
    INTENTS_PER_VALUE_TYPE.put(ValueType.DECISION, Arrays.asList(DecisionIntent.CREATED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.DECISION_REQUIREMENTS,
        Arrays.asList(DecisionRequirementsIntent.CREATED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.DECISION_EVALUATION,
        Arrays.asList(DecisionEvaluationIntent.FAILED, DecisionEvaluationIntent.EVALUATED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.JOB,
        Arrays.asList(JobIntent.CREATED, JobIntent.COMPLETED, JobIntent.CANCELED,
            JobIntent.TIMED_OUT, JobIntent.FAILED, JobIntent.RETRIES_UPDATED,
            JobIntent.ERROR_THROWN, JobIntent.RECURRED_AFTER_BACKOFF, JobIntent.YIELDED,
            JobIntent.TIMEOUT_UPDATED, JobIntent.MIGRATED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.INCIDENT,
        Arrays.asList(IncidentIntent.CREATED, IncidentIntent.RESOLVED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.PROCESS, Arrays.asList(ProcessIntent.CREATED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.PROCESS_INSTANCE,
        Arrays.asList(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN,
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED,
            ProcessInstanceIntent.ELEMENT_COMPLETING, ProcessInstanceIntent.ELEMENT_COMPLETED,
            ProcessInstanceIntent.ELEMENT_TERMINATING, ProcessInstanceIntent.ELEMENT_TERMINATED,
            ProcessInstanceIntent.ELEMENT_MIGRATED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.VARIABLE,
        Arrays.asList(VariableIntent.CREATED, VariableIntent.UPDATED));
    INTENTS_PER_VALUE_TYPE.put(ValueType.VARIABLE_DOCUMENT, Arrays.asList());
    INTENTS_PER_VALUE_TYPE.put(ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        Arrays.asList(ProcessMessageSubscriptionIntent.CREATED));
  }

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

    exporter.configure(new ExporterTestContext()
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.operate.OperateElasticsearchExporterIT#getValueTypeIntentCombinations")
  public void shouldExportRecord(Tuple<ValueType, Intent> parameter) {

    // given
    final ValueType valueType = parameter.getLeft();
    final Intent intent = parameter.getRight();

    final Record<RecordValue> record = factory.generateRecord(valueType,
        b -> b.withIntent(intent).withRecordType(RecordType.EVENT));

    final List<ExportHandler<?, ?>> handlersForRecord =
        writer.getHandlersForValueType(record.getValueType());

    // when
    exporter.export(record);

    // then
    assertThat(handlersForRecord).isNotEmpty();

    for (ExportHandler<?, ?> handler : handlersForRecord) {

      final String expectedId = handler.generateId((Record) record);
      final String indexName = handler.getIndexName();

      final Map<String, Object> document =
          findElasticsearchDocument(indexName, expectedId, handler.getClass().getSimpleName());
      assertThat(document).isNotNull();
      System.out.println(String.format("Returned document %s", document));

    }

  }

  private Map<String, Object> findElasticsearchDocument(String index, String id,
      String handlerName) {

    try {
      schemaManager.refresh(index); // ensure latest data is visible
      final GetRequest request = new GetRequest(index).id(id);
      final GetResponse response = esClient.get(request, RequestOptions.DEFAULT);
      if (response.isExists()) {
        return response.getSourceAsMap();
      } else {
        throw new RuntimeException(
            String.format("Could not find document with id %s in index %s (based on handler %s)",
                id, index, handlerName));
      }

    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }


  public static Stream<Tuple<ValueType, Intent>> getValueTypeIntentCombinations() {
    return INTENTS_PER_VALUE_TYPE.entrySet().stream().flatMap(
        entry -> entry.getValue().stream().map(intent -> new Tuple<>(entry.getKey(), intent)));
  }

  public static Stream<ValueType> getSupportedValueTypes() {
    return Arrays.stream(ImportValueType.values())
        .map(operateType -> ValueType.valueOf(operateType.name()));
  }
}
