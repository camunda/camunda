/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.operate;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.ElasticsearchExporterException;
import io.camunda.zeebe.exporter.ElasticsearchMetrics;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.dto.BulkResponse;
import io.camunda.zeebe.exporter.dto.BulkResponse.Error;
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
import io.camunda.zeebe.exporter.operate.schema.indices.DecisionIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.zeebe.exporter.operate.schema.indices.ProcessIndex;
import io.camunda.zeebe.exporter.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.EventTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.IncidentTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.ListViewTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.zeebe.exporter.operate.schema.templates.VariableTemplate;
import io.camunda.zeebe.protocol.record.Record;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperateElasticsearchExporter implements Exporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperateElasticsearchExporter.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OperateElasticsearchExporterConfiguration configuration;
  private int batchSize; // stored separately so that we can change it easily in tests
  private Controller controller;
  private RestHighLevelClient esClient;
  private RequestManager requestManager;

  // dynamic state for the lifetime of the exporter
  private long lastExportedPosition = -1;
  private OperateElasticsearchManager schemaManager;
  private ExportBatchWriter writer;
  private ElasticsearchMetrics metrics;

  @Override
  public void configure(Context context) throws Exception {

    configuration =
        context.getConfiguration().instantiate(OperateElasticsearchExporterConfiguration.class);
    batchSize = configuration.getBulk().size;
    LOGGER.debug("Exporter configured with {}", configuration);

    context.setFilter(new OperateElasticSearchRecordFilter());
  }

  @Override
  public void open(Controller controller) {
    this.esClient = createEsClient();
    this.controller = controller;

    // TODO: init client
    schemaManager = new OperateElasticsearchManager(esClient);
    schemaManager.createSchema();

    this.writer = createBatchWriter();

    scheduleDelayedFlush();

    this.requestManager =
        new RequestManager(
            esClient.getLowLevelClient(),
            r -> {
              final BulkResponse response;
              try {
                // buffer the complete response in memory before parsing it; this will give us a
                // better error
                // message which contains the raw response should the deserialization fail
                final var responseBody = r.getEntity().getContent().readAllBytes();
                response = MAPPER.readValue(responseBody, BulkResponse.class);
              } catch (IOException e) {
                return new ElasticsearchExporterException("Failed to read bulk response", e);
              }

              if (response.errors()) {
                return createBulkErrorException(response);
              } else {
                return null;
              }
            },
            e -> new ElasticsearchExporterException("Could not send bulk request", e),
            e -> LOGGER.warn("Elasticsearch bulk request failed", e),
            configuration.getNumConcurrentRequests());
  }

  /*
   * TODO: could be interesting to get an array of currently avaiable records (e.g. sized to a
   * "batch" in zeebe, e.g. what is contained in the current Zeebe file buffer) => effect would be
   * to reduce latency by knowing that a small batch indicates that there is not more new data and
   * we can flush a smaller batch to Elasticsearch
   *
   * TODO: consider adding metrics that allows debugging the behavior of the exporter sufficiently
   */
  @Override
  public void export(Record<?> record) {
    if (metrics == null) {
      // we only know the partition id here
      metrics = new ElasticsearchMetrics(record.getPartitionId());
    }

    requestManager.resolveResponses();

    writer.addRecord(record);

    this.lastExportedPosition = record.getPosition();

    if (writer.hasAtLeastEntities(batchSize)) {
      flush();
    }
  }

  public ExportBatchWriter getWriter() {
    return writer;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  private void scheduleDelayedFlush() {
    controller.scheduleCancellableTask(
        Duration.ofSeconds(configuration.getBulk().delay), this::flushAndReschedule);
  }

  private void flushAndReschedule() {
    try {
      flush();
    } catch (final Exception e) {
      LOGGER.warn(
          "Unexpected exception occurred on periodically flushing bulk, will retry later.", e);
    }
    scheduleDelayedFlush();
  }

  /*
   * Public so that we can flush from tests at any point
   */
  public void flush() {

    final OperateElasticsearchBulkRequest request = new OperateElasticsearchBulkRequest();

    try (final Histogram.Timer ignored = metrics.measureFlushDuration()) {
      writer.flush(request);

      // TODO: restore request size metric
      //      metrics.recordBulkMemorySize(request.sizeInBytes());
      sendRequest(request, lastExportedPosition);
    }
  }

  /** for testing */
  public void flushSync() {
    flush();
    requestManager.resolveResponsesBlocking(20);
  }

  private void sendRequest(OperateElasticsearchBulkRequest request, long positionToAcknowledge) {
    final var lowLevelRequest = new Request("POST", "/_bulk");
    final var body = new EntityTemplate(request);
    body.setContentType("application/x-ndjson");
    lowLevelRequest.setEntity(body);

    requestManager.send(
        lowLevelRequest, r -> controller.updateLastExportedRecordPosition(positionToAcknowledge));
  }

  private ElasticsearchExporterException createBulkErrorException(final BulkResponse bulkResponse) {
    final var collectedErrors = new ArrayList<String>();
    bulkResponse.items().stream()
        .flatMap(
            item ->
                Optional.ofNullable(item.index())
                    .or(() -> Optional.ofNullable(item.update()))
                    .stream())
        .flatMap(result -> Optional.ofNullable(result.error()).stream())
        .collect(Collectors.groupingBy(Error::type))
        .forEach(
            (errorType, errors) ->
                collectedErrors.add(
                    String.format(
                        "Failed to flush %d item(s) of bulk request [type: %s, reason: %s]",
                        errors.size(), errorType, errors.get(0).reason())));

    return new ElasticsearchExporterException("Failed to flush bulk request: " + collectedErrors);
  }

  @Override
  public void close() {
    try {
      esClient.close();
    } catch (IOException e) {
      throw new RuntimeException("Could not close ES client", e);
    }
  }

  public OperateElasticsearchManager getSchemaManager() {
    return schemaManager;
  }

  private void updateLastExportedPosition() {
    controller.updateLastExportedRecordPosition(lastExportedPosition);
  }

  public RestHighLevelClient createEsClient() {
    LOGGER.debug("Creating Elasticsearch connection...");
    final RestClientBuilder restClientBuilder =
        RestClient.builder(parseUrlConfig(configuration.getUrl()))
            .setRequestConfigCallback(
                b ->
                    b.setConnectTimeout(configuration.getRequestTimeoutMs())
                        .setSocketTimeout(configuration.getRequestTimeoutMs()))
            .setHttpClientConfigCallback(b -> configureHttpClient(b));

    final RestHighLevelClient esClient =
        new RestHighLevelClientBuilder(restClientBuilder.build())
            .setApiCompatibilityMode(true)
            .build();
    return esClient;
  }

  private HttpHost[] parseUrlConfig(String urlConfig) {
    final var urls = urlConfig.split(",");
    final var hosts = new HttpHost[urls.length];

    for (int i = 0; i < urls.length; i++) {
      hosts[i] = HttpHost.create(urls[i]);
    }

    return hosts;
  }

  private HttpAsyncClientBuilder configureHttpClient(final HttpAsyncClientBuilder builder) {
    // use single thread for rest client
    builder.setDefaultIOReactorConfig(
        IOReactorConfig.custom()
            .setIoThreadCount(configuration.getNumConcurrentRequests())
            .setConnectTimeout(5000)
            .setSoTimeout(5000)
            .build());

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
            configuration.getAuthentication().getUsername(),
            configuration.getAuthentication().getPassword()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
  }

  private ExportBatchWriter createBatchWriter() {

    final OperateElasticsearchSoftHashMap<String, String> callActivityIdCache =
        new OperateElasticsearchSoftHashMap<>(configuration.getTreePathCacheSize());
    return ExportBatchWriter.Builder.begin()
        // ImportBulkProcessor
        // #processDecisionRecords
        .withHandler(
            new DecisionDefinitionHandler(schemaManager.getIndexDescriptor(DecisionIndex.class)))
        // #processDecisionRequirementsRecord
        .withHandler(
            new DecisionRequirementsHandler(
                schemaManager.getIndexDescriptor(DecisionRequirementsIndex.class)))
        // #processDecisionEvaluationRecords
        // TODO: can covert this to a proper handler some day in the future, if we can handle
        // transforming one record
        // to multiple entities
        .withDecisionInstanceHandler(
            new DecisionInstanceHandler(
                schemaManager.getTemplateDescriptor(DecisionInstanceTemplate.class)))
        // #processProcessInstanceRecords
        // FlowNodeInstanceZeebeRecordProcessor#processProcessInstanceRecord
        .withHandler(
            new FlowNodeInstanceHandler(
                schemaManager.getTemplateDescriptor(FlowNodeInstanceTemplate.class),
                configuration,
                callActivityIdCache,
                esClient))
        // eventZeebeRecordProcessor.processProcessInstanceRecords
        .withHandler(
            new EventFromProcessInstanceHandler(
                schemaManager.getTemplateDescriptor(EventTemplate.class)))
        // sequenceFlowZeebeRecordProcessor.processSequenceFlowRecord
        .withHandler(
            new SequenceFlowHandler(
                schemaManager.getTemplateDescriptor(SequenceFlowTemplate.class)))
        // listViewZeebeRecordProcessor.processProcessInstanceRecord
        .withHandler(
            new ListViewFromProcessInstanceHandler(
                schemaManager.getTemplateDescriptor(ListViewTemplate.class),
                configuration,
                callActivityIdCache,
                esClient))
        // TODO: need to choose between upsert and insert (see optimization in
        // FlowNodeInstanceZeebeRecordProcessor#canOptimizeFlowNodeInstanceIndexing)
        .withHandler(
            new ListViewFromActivityInstanceHandler(
                schemaManager.getTemplateDescriptor(ListViewTemplate.class)))
        // #processIncidentRecords
        // incidentZeebeRecordProcessor.processIncidentRecord
        .withHandler(
            new IncidentHandler(schemaManager.getTemplateDescriptor(IncidentTemplate.class)))
        .withHandler(
            new PostImporterQueueHandler(
                schemaManager.getTemplateDescriptor(PostImporterQueueTemplate.class)))

        // TODO: These two are in conflict: they create entities with the same id in different
        // indexes

        // listViewZeebeRecordProcessor.processIncidentRecord
        .withHandler(
            new ListViewFromIncidentHandler(
                schemaManager.getTemplateDescriptor(ListViewTemplate.class)))
        // flowNodeInstanceZeebeRecordProcessor.processIncidentRecord
        .withHandler(
            new FlowNodeInstanceFromIncidentHandler(
                schemaManager.getTemplateDescriptor(FlowNodeInstanceTemplate.class)))

        // eventZeebeRecordProcessor.processIncidentRecords
        .withHandler(
            new EventFromIncidentHandler(schemaManager.getTemplateDescriptor(EventTemplate.class)))
        // #processVariableRecords
        // listViewZeebeRecordProcessor.processVariableRecords
        .withHandler(
            new ListViewFromVariableHandler(
                schemaManager.getTemplateDescriptor(ListViewTemplate.class)))
        // variableZeebeRecordProcessor.processVariableRecords
        .withHandler(
            new VariableHandler(schemaManager.getTemplateDescriptor(VariableTemplate.class)))
        // #processVariableDocumentRecords
        // operationZeebeRecordProcessor.processVariableDocumentRecords
        // TODO: currently not implemented; is needed to complete operations
        // #processProcessRecords
        // processZeebeRecordProcessor.processDeploymentRecord
        .withHandler(new ProcessHandler(schemaManager.getIndexDescriptor(ProcessIndex.class)))
        // #processJobRecords
        // listViewZeebeRecordProcessor.processJobRecords
        .withHandler(
            new ListViewFromJobHandler(schemaManager.getTemplateDescriptor(ListViewTemplate.class)))
        // eventZeebeRecordProcessor.processJobRecords
        .withHandler(
            new EventFromJobHandler(schemaManager.getTemplateDescriptor(EventTemplate.class)))
        // #processProcessMessageSubscription
        // eventZeebeRecordProcessor.processProcessMessageSubscription
        .withHandler(
            new EventFromMessageSubscriptionHandler(
                schemaManager.getTemplateDescriptor(EventTemplate.class)))
        .build();
  }
}
