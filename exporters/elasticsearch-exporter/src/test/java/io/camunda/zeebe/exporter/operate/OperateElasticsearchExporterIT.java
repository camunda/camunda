package io.camunda.zeebe.exporter.operate;

import static org.assertj.core.api.Assertions.assertThat;
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
import co.elastic.clients.elasticsearch.core.GetResponse;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
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
  private ExporterTestController controller;
  private TestClient testClient;
  
  @BeforeAll
  public void beforeAll() {
    config.url = CONTAINER.getHttpHostAddress();
    // TODO: who is creating the indexes? exporter or operate? how do we want to do it in the test then?
//    config.index.setNumberOfShards(1);
//    config.index.setNumberOfReplicas(1);
//    config.index.createTemplate = true;
    config.getBulk().size = 1; // force flushing on the first record

    // TODO: index router also depends on where and how we create the indexes
//    testClient = new TestClient(config, indexRouter);

  }
  
  @BeforeEach
  public void setUpExporter() throws Exception {
    controller =  new ExporterTestController();
    
    exporter = new OperateElasticsearchExporter();
    
    exporter.configure(
        new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("elastic", config)));
    exporter.open(controller);
  }
  
  @AfterEach
  public void tearDownExporter() {
    exporter.close();
    exporter = null;
    controller = null;
  }
  
  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
  public void shouldExportRecord(ValueType valueType) {
    // given
    final Record<RecordValue> record = factory.generateRecord(valueType);

    // when
    exporter.export(record);

    // then
    // TODO: must use different assertions to look up the results in Elasticsearch
//    GetResponse<Record> response = testClient.getExportedDocumentFor(record);
//    assertThat(response)
//        .extracting(GetResponse::index, GetResponse::id, GetResponse::routing, GetResponse::source)
//        .containsExactly(
//            indexRouter.indexFor(record),
//            indexRouter.idFor(record),
//            String.valueOf(record.getPartitionId()),
//            record);
  }
}
