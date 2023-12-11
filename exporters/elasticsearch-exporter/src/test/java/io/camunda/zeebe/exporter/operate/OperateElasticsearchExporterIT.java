package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
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
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.RestClientFactory;
import io.camunda.zeebe.exporter.TestClient;
import io.camunda.zeebe.exporter.TestSupport;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;

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
  @MethodSource("io.camunda.zeebe.exporter.operate.OperateElasticsearchExporterIT#getSupportedValueTypes")
  public void shouldExportRecord(ValueType valueType) {
    // given
    final Record<RecordValue> record = factory.generateRecord(valueType);

    List<ExportHandler<?, ?>> handlersForRecord =
        writer.getHandlersForValueType(record.getValueType());

    // when
    exporter.export(record);

    // then
    assertThat(handlersForRecord).isNotEmpty();

    for (ExportHandler<?, ?> handler : handlersForRecord) {
      
      String expectedId = handler.generateId((Record) record);
      String indexName = handler.getIndexName();
      
      Map<String, Object> document = findElasticsearchDocument(indexName, expectedId, handler.getClass().getSimpleName());
      assertThat(document).isNotNull();
      System.out.println(String.format("Returned document %s", document));
      
    }

  }
  
  private Map<String, Object> findElasticsearchDocument(String index, String id, String handlerName) {
    
    try {
      schemaManager.refresh(index); // ensure latest data is visible
      final GetRequest request = new GetRequest(index).id(id);
      final GetResponse response = esClient.get(request, RequestOptions.DEFAULT);
      if(response.isExists()) {
        return response.getSourceAsMap();
      } else {
        throw new RuntimeException(String.format("Could not find document with id %s in index %s (based on handler %s)", id, index, handlerName));
      }
      
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Stream<ValueType> getSupportedValueTypes() {
    return Arrays.stream(ImportValueType.values())
        .map(operateType -> ValueType.valueOf(operateType.name()));
  }
}
