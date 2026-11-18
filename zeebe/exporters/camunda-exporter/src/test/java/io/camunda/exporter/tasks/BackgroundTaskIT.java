/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import static io.camunda.exporter.utils.CamundaExporterSchemaUtils.createSchemas;
import static io.camunda.search.test.utils.SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.camunda.exporter.DefaultExporterResourceProvider;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiverRepository;
import io.camunda.exporter.tasks.archiver.ElasticsearchArchiverRepository;
import io.camunda.exporter.tasks.archiver.OpenSearchArchiverRepository;
import io.camunda.exporter.utils.CamundaExporterITTemplateExtension;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.AgentHistoryTemplate;
import io.camunda.webapps.schema.descriptors.template.AgentInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.AuditLogTemplate;
import io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.template.WaitStateTemplate;
import io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.JobEntity;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.agrona.CloseHelper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BackgroundTaskIT<T extends BackgroundTask> {
  protected static final Logger LOGGER = LoggerFactory.getLogger(BackgroundTaskIT.class);
  protected static final int PARTITION_ID = 1;
  protected static final AtomicLong ID_GENERATOR = new AtomicLong(1);
  protected static final Instant NOW = Instant.parse("2026-05-01T10:26:00Z");
  protected static final Duration EXECUTE_TIMEOUT = Duration.ofSeconds(30L);

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @RegisterExtension
  private static CamundaExporterITTemplateExtension templateExtension =
      new CamundaExporterITTemplateExtension(searchDB);

  protected CamundaExporterMetrics exporterMetrics;
  protected ExecutorService executor;
  protected Context context;
  protected String testPrefix;

  private final List<AutoCloseable> resourcesToClose = new ArrayList<>();

  @BeforeEach
  protected void setup() {
    context =
        new ExporterTestContext().setPartitionId(PARTITION_ID).setClock(InstantSource.fixed(NOW));
    exporterMetrics = mock(CamundaExporterMetrics.class);
    executor = Executors.newSingleThreadExecutor();
    testPrefix = RandomStringUtils.insecure().nextAlphabetic(9).toLowerCase(Locale.ROOT);
  }

  @AfterEach
  protected void teardown() throws Exception {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      searchDB.esClient().indices().delete(req -> req.index(testPrefix + "*"));
    }
    searchDB.osClient().indices().delete(req -> req.index(testPrefix + "*"));

    if (executor != null) {
      executor.shutdown();
      executor = null;
    }

    CloseHelper.quietCloseAll(resourcesToClose);
    resourcesToClose.clear();
  }

  @TestTemplate
  protected void shouldExecuteWithoutErrorsWhenNothingToDo(
      final ExporterConfiguration config, final SearchClientAdapter ignored) throws Exception {
    // just a basic smoke test to verify that the task runs and creates well-formed queries etc
    // when there's nothing to actually do
    withTask(
        config,
        (task, resources) -> {
          // when
          final var archived = task.execute();

          // then
          assertThat(archived).succeedsWithin(EXECUTE_TIMEOUT).isEqualTo(0);
        });
  }

  protected void verifyDependentEntitiesSpecifiedInTests(final ExporterConfiguration config)
      throws Exception {
    // this is self-test, so we ensure we are exercising the archiver/deleter for all dependant
    // entities. That way the test should get updated if a new dependant entity is added to the
    // exporter, and we don't forget to update the archiver/deleter to handle it.
    withTask(
        config,
        (job, resourceProvider) -> {
          // given
          final var expectedProcessInstanceDependent =
              resourceProvider.getIndexTemplateDescriptors().stream()
                  .filter(ProcessInstanceDependant.class::isInstance)
                  .collect(Collectors.toSet());

          final ProcessInstanceForListViewEntity processInstance =
              processInstanceForListViewEntity("2020-01-01T00:00:00+00:00");

          // when
          final var processInstanceDependent =
              getProcessInstanceDependentEntities(resourceProvider, processInstance).stream()
                  .map(DependentEntities::template)
                  .collect(Collectors.toSet());

          // then
          assertThat(processInstanceDependent).isEqualTo(expectedProcessInstanceDependent);
        });
  }

  protected abstract T createBackgroundTask(
      ExporterConfiguration config, ExporterResourceProvider resourceProvider);

  protected void updateConfig(final ExporterConfiguration config) {
    // default implementation does nothing
  }

  protected void withTask(
      final ExporterConfiguration config, final BackgroundTaskConsumer<T> taskConsumer)
      throws Exception {
    config.getConnect().setIndexPrefix(testPrefix);
    config.getIndex().setNumberOfShards(3);
    config.getIndex().setNumberOfReplicas(0);
    updateConfig(config);
    createSchemas(config);

    final ExporterResourceProvider exporterResourceProvider = exporterResourceProvider(config);

    try (final T task = createBackgroundTask(config, exporterResourceProvider)) {
      taskConsumer.accept(task, exporterResourceProvider);
    }
  }

  private ExporterResourceProvider exporterResourceProvider(final ExporterConfiguration config) {
    final var cacheProvider = mock(ExporterEntityCacheProvider.class);
    when(cacheProvider.getProcessCacheLoader(anyString(), any())).thenReturn(k -> null);
    when(cacheProvider.getBatchOperationCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getDecisionRequirementsCacheLoader(anyString())).thenReturn(k -> null);
    when(cacheProvider.getFormCacheLoader(anyString())).thenReturn(k -> null);
    final var resourceProvider = new DefaultExporterResourceProvider();
    resourceProvider.init(
        config,
        cacheProvider,
        context,
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());
    return resourceProvider;
  }

  protected void store(
      final IndexDescriptor indexDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> entity)
      throws IOException {
    client.index(entity.getId(), indexDescriptor.getFullQualifiedName(), entity);
  }

  protected void store(
      final IndexDescriptor indexDescriptor,
      final SearchClientAdapter client,
      final ExporterEntity<?> parent,
      final ExporterEntity<?> child)
      throws IOException {
    client.index(child.getId(), parent.getId(), indexDescriptor.getFullQualifiedName(), child);
  }

  protected ElasticsearchAsyncClient createAsyncESClient(final ExporterConfiguration config) {
    final var connector = new ElasticsearchConnector(config.getConnect());
    return connector.createAsyncClient();
  }

  protected OpenSearchAsyncClient createOSAsyncClient(final ExporterConfiguration config) {
    final var connector = new OpensearchConnector(config.getConnect());
    return connector.createAsyncClient();
  }

  protected ArchiverRepository createArchiverRepository(
      final ExporterConfiguration config, final ExporterResourceProvider resourceProvider) {
    final var isElasticsearch = ConnectionTypes.isElasticSearch(config.getConnect().getType());
    if (isElasticsearch) {
      return closeLater(
          new ElasticsearchArchiverRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              createAsyncESClient(config),
              executor,
              exporterMetrics,
              LOGGER));
    } else {
      final var asyncClient = createOSAsyncClient(config);
      final var genericClient =
          new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions());

      return closeLater(
          new OpenSearchArchiverRepository(
              PARTITION_ID,
              config.getHistory(),
              resourceProvider,
              asyncClient,
              genericClient,
              executor,
              exporterMetrics,
              LOGGER));
    }
  }

  protected <R extends AutoCloseable> R closeLater(final R resource) {
    resourcesToClose.add(resource);
    return resource;
  }

  protected List<DependentEntities> getProcessInstanceDependentEntities(
      final ExporterResourceProvider resourceProvider,
      final ProcessInstanceForListViewEntity processInstance) {
    return List.of(
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class),
            List.of(
                flowNodeInstanceEntity(processInstance), flowNodeInstanceEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(VariableTemplate.class),
            List.of(variableEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(IncidentTemplate.class),
            List.of(incidentEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(JobTemplate.class),
            List.of(jobEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class),
            List.of(operationEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(MessageSubscriptionTemplate.class),
            List.of(messageSubscriptionEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(
                CorrelatedMessageSubscriptionTemplate.class),
            List.of(correlatedMessageSubscriptionEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(SequenceFlowTemplate.class),
            List.of(sequenceFlowEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(PostImporterQueueTemplate.class),
            List.of(postImporterQueueEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(SnapshotTaskVariableTemplate.class),
            List.of(snapshotTaskVariableEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(DecisionInstanceTemplate.class),
            List.of(decisionInstanceEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(AuditLogTemplate.class),
            List.of(auditLogEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(WaitStateTemplate.class),
            List.of(waitStateEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(TaskTemplate.class),
            List.of(taskEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(AgentInstanceTemplate.class),
            List.of(agentInstanceEntity(processInstance))),
        new DependentEntities(
            resourceProvider.getIndexTemplateDescriptor(AgentHistoryTemplate.class),
            List.of(agentHistoryEntity(processInstance))));
  }

  protected ProcessInstanceForListViewEntity processInstanceForListViewEntity(
      final String endDate) {
    final ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity();
    final long id = ID_GENERATOR.incrementAndGet();
    processInstance.setId(String.valueOf(id));
    processInstance.setKey(id);
    processInstance.setPartitionId(PARTITION_ID);
    if (endDate != null) {
      processInstance.setEndDate(OffsetDateTime.parse(endDate));
    }

    return processInstance;
  }

  protected FlowNodeInstanceForListViewEntity flowNodeInstanceForListViewEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final FlowNodeInstanceForListViewEntity entity = create(FlowNodeInstanceForListViewEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    entity.getJoinRelation().setParent(processInstance.getKey());

    return entity;
  }

  protected VariableForListViewEntity variableForListViewEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final VariableForListViewEntity entity = create(VariableForListViewEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    entity.getJoinRelation().setParent(processInstance.getKey());

    return entity;
  }

  protected FlowNodeInstanceEntity flowNodeInstanceEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final FlowNodeInstanceEntity entity = create(FlowNodeInstanceEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  protected VariableEntity variableEntity(final ProcessInstanceForListViewEntity processInstance) {
    final VariableEntity entity = create(VariableEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  protected IncidentEntity incidentEntity(final ProcessInstanceForListViewEntity processInstance) {
    final IncidentEntity entity = create(IncidentEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    entity.setErrorMessage("Error message");

    return entity;
  }

  protected JobEntity jobEntity(final ProcessInstanceForListViewEntity processInstance) {
    final JobEntity entity = create(JobEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  protected OperationEntity operationEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final OperationEntity entity = create(OperationEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  protected MessageSubscriptionEntity messageSubscriptionEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final MessageSubscriptionEntity entity = create(MessageSubscriptionEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  protected CorrelatedMessageSubscriptionEntity correlatedMessageSubscriptionEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final CorrelatedMessageSubscriptionEntity entity =
        create(CorrelatedMessageSubscriptionEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());

    return entity;
  }

  protected SequenceFlowEntity sequenceFlowEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final SequenceFlowEntity entity = create(SequenceFlowEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected PostImporterQueueEntity postImporterQueueEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final PostImporterQueueEntity entity = create(PostImporterQueueEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected SnapshotTaskVariableEntity snapshotTaskVariableEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final SnapshotTaskVariableEntity entity = create(SnapshotTaskVariableEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected DecisionInstanceEntity decisionInstanceEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final DecisionInstanceEntity entity = create(DecisionInstanceEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected AuditLogEntity auditLogEntity(final ProcessInstanceForListViewEntity processInstance) {
    final AuditLogEntity entity = create(AuditLogEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected WaitStateEntity waitStateEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final WaitStateEntity entity = create(WaitStateEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected TaskEntity taskEntity(final ProcessInstanceForListViewEntity processInstance) {
    final TaskEntity entity = create(TaskEntity::new);
    entity.setProcessInstanceId(String.valueOf(processInstance.getKey()));
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected AgentInstanceEntity agentInstanceEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final AgentInstanceEntity entity = create(AgentInstanceEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected AgentHistoryEntity agentHistoryEntity(
      final ProcessInstanceForListViewEntity processInstance) {
    final AgentHistoryEntity entity = create(AgentHistoryEntity::new);
    entity.setProcessInstanceKey(processInstance.getKey());
    entity.setRootProcessInstanceKey(processInstance.getKey());
    return entity;
  }

  protected <E extends ExporterEntity<E>> E create(final Supplier<E> constructor) {
    final long id = ID_GENERATOR.incrementAndGet();
    final var entity = constructor.get();
    entity.setId(String.valueOf(id));
    return entity;
  }

  protected record DependentEntities(
      IndexTemplateDescriptor template, List<ExporterEntity<?>> entities) {}

  protected interface BackgroundTaskConsumer<T extends BackgroundTask> {
    void accept(T task, ExporterResourceProvider resourceProvider) throws Exception;
  }
}
