package io.camunda.zeebe.exporter.operate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.util.RetryOperation;
import io.camunda.zeebe.exporter.ElasticsearchExporterConfiguration;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.operate.handlers.DecisionDefinitionHandler;
import io.camunda.zeebe.exporter.operate.handlers.DecisionInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.DecisionRequirementsHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromIncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromJobHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromMessageSubscriptionHandler;
import io.camunda.zeebe.exporter.operate.handlers.EventFromProcessInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.FlowNodeInstanceFromIncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.FlowNodeInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.IncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromActivityInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromIncidentHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromJobHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromProcessInstanceHandler;
import io.camunda.zeebe.exporter.operate.handlers.ListViewFromVariableHandler;
import io.camunda.zeebe.exporter.operate.handlers.PostImporterQueueHandler;
import io.camunda.zeebe.exporter.operate.handlers.ProcessHandler;
import io.camunda.zeebe.exporter.operate.handlers.SequenceFlowHandler;
import io.camunda.zeebe.exporter.operate.handlers.VariableHandler;
import io.camunda.zeebe.protocol.record.Record;

public class OperateElasticsearchExporter implements Exporter {

  private static final Logger logger = LoggerFactory.getLogger(OperateElasticsearchExporter.class);
  
  private OperateElasticsearchExporterConfiguration configuration;
  private RestHighLevelClient esClient;

  private Controller controller;

  // dynamic state for the lifetime of the exporter
  private long lastExportedPosition = -1;
  
  // dynamic state for the current batch of ES writes
  private NoSpringElasticsearchBatchRequest request;
  private ExportBatchWriter writer;
  
  @Override
  public void configure(Context context) throws Exception {
    
    configuration =
        context.getConfiguration().instantiate(OperateElasticsearchExporterConfiguration.class);
    logger.debug("Exporter configured with {}", configuration);

    // TODO: filter here to only handle events (and potentially also only certain event types)
//    context.setFilter(new ElasticsearchRecordFilter(configuration));
    
  }
  
  @Override
  public void open(Controller controller) {
     this.esClient = createEsClient();
     this.controller = controller;

     scheduleDelayedFlush();
  }
  
  @Override
  public void export(Record<?> record) {
    if (request == null) {
      request = new NoSpringElasticsearchBatchRequest(esClient);
      writer = createBatchWriter(request);
    }
   
    writer.addRecord(record);

    this.lastExportedPosition = record.getPosition();
    
    if (writer.hasAtLeastEntities(configuration.getBulk().size)) {
      try {
        flush();
      } catch (PersistenceException e) {
        // TODO: in this case we would send an entity with the same ID twice, not sure what happens then
        //       risk is acceptable for a prototype
        throw new RuntimeException("Could not flush during regular export. "
            + "This indicates a bug because this exporter is not retryable", e);
      }
    }
  }
  
  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(configuration.getBulk().delay), this::flushAndReschedule);
  }
  
  private void flushAndReschedule() {
    try {
      flush();
      updateLastExportedPosition();
    } catch (final Exception e) {
      logger.warn("Unexpected exception occurred on periodically flushing bulk, will retry later.", e);
    }
    scheduleDelayedFlush();
  }
  
  private void flush() throws PersistenceException {
    writer.flush();
    
    writer = null;
    request = null;
  }

  @Override
  public void close() {
    try {
      esClient.close();
    } catch (IOException e) {
      throw new RuntimeException("Could not close ES client", e);
    }
  }
  
  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastExportedPosition);
  }
  
  private RestHighLevelClient createEsClient() {
    logger.debug("Creating Elasticsearch connection...");
    final RestClientBuilder restClientBuilder =
        RestClient.builder(configuration.getUrl())
        .setRequestConfigCallback(
            b ->
                b.setConnectTimeout(configuration.getRequestTimeoutMs())
                    .setSocketTimeout(configuration.getRequestTimeoutMs()))
        .setHttpClientConfigCallback(b -> configureHttpClient(b));

    final RestHighLevelClient esClient = new RestHighLevelClientBuilder(restClientBuilder.build())
        .setApiCompatibilityMode(true).build();
    return esClient;
  }
  
  private HttpAsyncClientBuilder configureHttpClient(final HttpAsyncClientBuilder builder) {
    // use single thread for rest client
    builder.setDefaultIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(1).build());

    if (configuration.hasAuthenticationPresent()) {
      setupBasicAuthentication(builder);
    }

    return builder;
  }
  
  private void setupBasicAuthentication(final HttpAsyncClientBuilder builder) {
    final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(
            configuration.getAuthentication().getUsername(), configuration.getAuthentication().getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
  }
  
  private ExportBatchWriter createBatchWriter(NoSpringElasticsearchBatchRequest request) {

    return ExportBatchWriter.Builder.forRequest(request)
        // ImportBulkProcessor
        // #processDecisionRecords
        .withHandler(new DecisionDefinitionHandler())
        // #processDecisionRequirementsRecord
        .withHandler(new DecisionRequirementsHandler())
        // #processDecisionEvaluationRecords
        // TODO: can covert this to a proper handler some day in the future, if we can handle transforming one record
        //   to multiple entities
        .withDecisionInstanceHandler(new DecisionInstanceHandler())
        // #processProcessInstanceRecords
        //   FlowNodeInstanceZeebeRecordProcessor#processProcessInstanceRecord
        .withHandler(new FlowNodeInstanceHandler())
        //   eventZeebeRecordProcessor.processProcessInstanceRecords
        .withHandler(new EventFromProcessInstanceHandler())
        //   sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord
        .withHandler(new SequenceFlowHandler())
        //   listViewZeebeRecordProcessor.processProcessInstanceRecord
        .withHandler(new ListViewFromProcessInstanceHandler())
        // TODO: need to choose between upsert and insert (see optimization in
        // FlowNodeInstanceZeebeRecordProcessor#canOptimizeFlowNodeInstanceIndexing)
        .withHandler(new ListViewFromActivityInstanceHandler())
        // #processIncidentRecords
        //   incidentZeebeRecordProcessor.processIncidentRecord
        .withHandler(new IncidentHandler())
        .withHandler(new PostImporterQueueHandler())
        //   listViewZeebeRecordProcessor.processIncidentRecord
        .withHandler(new ListViewFromIncidentHandler())
        //   flowNodeInstanceZeebeRecordProcessor.processIncidentRecord
        .withHandler(new FlowNodeInstanceFromIncidentHandler())
        //   eventZeebeRecordProcessor.processIncidentRecords
        .withHandler(new EventFromIncidentHandler())
        // #processVariableRecords
        //   listViewZeebeRecordProcessor.processVariableRecords
        .withHandler(new ListViewFromVariableHandler())
        //   variableZeebeRecordProcessor.processVariableRecords
        .withHandler(new VariableHandler())
        // #processVariableDocumentRecords
        //   operationZeebeRecordProcessor.processVariableDocumentRecords
        //   TODO: currently not implemented; is needed to complete operations
        // #processProcessRecords
        //   processZeebeRecordProcessor.processDeploymentRecord
        .withHandler(new ProcessHandler())
        // #processJobRecords
        //   listViewZeebeRecordProcessor.processJobRecords
        .withHandler(new ListViewFromJobHandler())
        //   eventZeebeRecordProcessor.processJobRecords
        .withHandler(new EventFromJobHandler())
        // #processProcessMessageSubscription
        //   eventZeebeRecordProcessor.processProcessMessageSubscription
        .withHandler(new EventFromMessageSubscriptionHandler())
        .build();
  }
}
