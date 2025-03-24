/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import static io.camunda.search.test.utils.SearchDBExtension.INCIDENT_IDX_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ActiveIncident;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.Document;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.DocumentUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentBulkUpdate;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.IncidentDocument;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.PendingIncidentUpdateBatch;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.ProcessInstanceDocument;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.webapps.schema.entities.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.post.PostImporterQueueEntity;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("resource")
@Testcontainers
@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
abstract class IncidentUpdateRepositoryIT {
  public static final int PARTITION_ID = 1;
  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentUpdateRepositoryIT.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  protected final PostImporterQueueTemplate postImporterQueueTemplate;
  protected final IncidentTemplate incidentTemplate;
  protected final ListViewTemplate listViewTemplate;
  protected final FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  protected final OperationTemplate operationTemplate;
  @AutoClose private final ClientAdapter clientAdapter;
  private final SearchEngineClient engineClient;

  protected IncidentUpdateRepositoryIT(final String databaseUrl, final boolean isElastic) {
    final var config = new ExporterConfiguration();
    final var indexPrefix = INCIDENT_IDX_PREFIX + UUID.randomUUID();
    config.getConnect().setIndexPrefix(indexPrefix);
    config.getConnect().setUrl(databaseUrl);
    config.getConnect().setType(isElastic ? "elasticsearch" : "opensearch");

    clientAdapter = ClientAdapter.of(config.getConnect());
    engineClient = clientAdapter.getSearchEngineClient();

    postImporterQueueTemplate = new PostImporterQueueTemplate(indexPrefix, isElastic);
    incidentTemplate = new IncidentTemplate(indexPrefix, isElastic);
    listViewTemplate = new ListViewTemplate(indexPrefix, isElastic);
    flowNodeInstanceTemplate = new FlowNodeInstanceTemplate(indexPrefix, isElastic);
    operationTemplate = new OperationTemplate(indexPrefix, isElastic);
  }

  @BeforeEach
  void beforeEach() {
    Stream.of(
            postImporterQueueTemplate,
            incidentTemplate,
            listViewTemplate,
            flowNodeInstanceTemplate,
            operationTemplate)
        .forEach(
            template -> engineClient.createIndexTemplate(template, new IndexConfiguration(), true));
  }

  private IncidentEntity newIncident(final long key) {
    final var incident = new IncidentEntity();
    final var id = String.valueOf(key);

    incident.setState(IncidentState.PENDING);
    incident.setId(id);
    incident.setKey(key);
    incident.setProcessInstanceKey(key);
    incident.setFlowNodeInstanceKey(key);
    incident.setFlowNodeId(id);
    incident.setPartitionId(PARTITION_ID);
    incident.setErrorMessage("failure");

    return incident;
  }

  protected abstract IncidentUpdateRepository createRepository();

  protected abstract <T> Collection<T> search(
      final String index, final String field, final List<String> terms, final Class<T> documentType)
      throws IOException;

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class GetIncidentDocumentsTest {
    @Test
    void shouldReturnEmptyMap() {
      // given
      final var repository = createRepository();

      // when
      final var documents = repository.getIncidentDocuments(List.of("1"));

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.map(Long.class, IncidentDocument.class))
          .isEmpty();
    }

    @Test
    void shouldReturnIncidentByIds() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var expected = createIncident(1L);
      createIncident(2L);

      // when
      final var documents = repository.getIncidentDocuments(List.of("1"));

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.map(String.class, IncidentDocument.class))
          .hasSize(1)
          .containsEntry(
              "1", new IncidentDocument("1", incidentTemplate.getFullQualifiedName(), expected));
    }

    @RegressionTest("https://github.com/camunda/camunda/issues/25968")
    void shouldReturnMoreThanOnePage() throws PersistenceException {
      // given
      final var repository = createRepository();
      final List<String> ids = new ArrayList<>();
      final Map<String, IncidentDocument> expected = new HashMap<>();
      for (int i = 0; i < 20; i++) {
        final var id = String.valueOf(i);
        final var entity = createIncident(i);
        ids.add(id);
        expected.put(id, new IncidentDocument(id, incidentTemplate.getFullQualifiedName(), entity));
      }

      // when
      final var documents = repository.getIncidentDocuments(ids);

      // then
      assertThat(documents)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.map(String.class, IncidentDocument.class))
          .hasSize(20)
          .containsExactlyEntriesOf(expected);
    }

    private IncidentEntity createIncident(final long key) throws PersistenceException {
      final var incident = newIncident(key);

      indexIncident(incident);
      return incident;
    }

    private void indexIncident(final IncidentEntity incident) throws PersistenceException {
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(incidentTemplate.getFullQualifiedName(), incident);
      batchRequest.executeWithRefresh();
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class GetPendingIncidentsBatchTest {
    @Test
    void shouldGetOnlyNewUpdatesByPosition() throws PersistenceException {
      // given
      final var repository = createRepository();
      setupIncidentUpdates(1, 2);

      // when
      final var batch = repository.getPendingIncidentsBatch(1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(
              PendingIncidentUpdateBatch::highestPosition,
              PendingIncidentUpdateBatch::newIncidentStates)
          .containsExactly(2L, Map.of(2L, IncidentState.ACTIVE));
    }

    @Test
    void shouldKeepOnlyLatestUpdatePerKey() throws PersistenceException {
      // given
      final var repository = createRepository();
      setupIncidentUpdates(
          1,
          2L,
          e ->
              e.setKey(1L)
                  .setIntent(
                      e.getPosition() == 2
                          ? IncidentIntent.RESOLVED.name()
                          : IncidentIntent.CREATED.name()));

      // when
      final var batch = repository.getPendingIncidentsBatch(1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .returns(2L, PendingIncidentUpdateBatch::highestPosition)
          .extracting(PendingIncidentUpdateBatch::newIncidentStates)
          .asInstanceOf(InstanceOfAssertFactories.map(Long.class, IncidentState.class))
          .hasSize(1)
          .containsEntry(1L, IncidentState.RESOLVED);
    }

    @Test
    void shouldGetUpdates() throws PersistenceException {
      // given - incident 1 is resolved, incident 2 is created
      final var repository = createRepository();
      setupIncidentUpdates(
          1,
          2,
          e ->
              e.setIntent(
                  e.getKey() == 1
                      ? IncidentIntent.RESOLVED.name()
                      : IncidentIntent.CREATED.name()));

      // when
      final var batch = repository.getPendingIncidentsBatch(-1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(PendingIncidentUpdateBatch::newIncidentStates)
          .asInstanceOf(InstanceOfAssertFactories.map(Long.class, IncidentState.class))
          .hasSize(2)
          .containsEntry(1L, IncidentState.RESOLVED)
          .containsEntry(2L, IncidentState.ACTIVE);
    }

    @Test
    void shouldGetByPartitionId() throws PersistenceException {
      // given - incident 1 on partition 1, incident 2 on partition 2
      final var repository = createRepository();
      setupIncidentUpdates(1, 2, e -> e.setPartitionId(Math.toIntExact(e.getKey())));

      // when
      final var batch = repository.getPendingIncidentsBatch(-1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(
              PendingIncidentUpdateBatch::highestPosition,
              PendingIncidentUpdateBatch::newIncidentStates)
          .containsExactly(1L, Map.of(1L, IncidentState.ACTIVE));
    }

    @Test
    void shouldReturnEmptyBatch() {
      // given
      final var repository = createRepository();

      // when
      final var batch = repository.getPendingIncidentsBatch(-1L, 10);

      // then
      assertThat(batch)
          .succeedsWithin(REQUEST_TIMEOUT)
          .extracting(
              PendingIncidentUpdateBatch::highestPosition,
              PendingIncidentUpdateBatch::newIncidentStates)
          .containsExactly(-1L, Collections.emptyMap());
    }

    private PostImporterQueueEntity newPendingUpdate() {
      return new PostImporterQueueEntity()
          .setActionType(PostImporterActionType.INCIDENT)
          .setIntent(IncidentIntent.CREATED.name())
          .setKey(1L)
          .setPartitionId(PARTITION_ID)
          .setProcessInstanceKey(1L)
          .setPosition(1L);
    }

    private void setupIncidentUpdates(final long fromPosition, final long toPosition)
        throws PersistenceException {
      setupIncidentUpdates(fromPosition, toPosition, ignored -> {});
    }

    private void setupIncidentUpdates(
        final long fromPosition,
        final long toPosition,
        final Consumer<PostImporterQueueEntity> modifier)
        throws PersistenceException {
      final var updates =
          LongStream.rangeClosed(fromPosition, toPosition)
              .mapToObj(position -> newPendingUpdate().setPosition(position).setKey(position))
              .peek(modifier)
              .toList();
      final var batchRequest = clientAdapter.createBatchRequest();
      updates.forEach(e -> batchRequest.add(postImporterQueueTemplate.getFullQualifiedName(), e));
      batchRequest.executeWithRefresh();
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class BulkUpdateIT {
    @Test
    void shouldReportError() {
      // given
      final var repository = createRepository();
      final var bulk = new IncidentBulkUpdate();

      // when
      bulk.incidentRequests().put("2", new DocumentUpdate("2", "doesn't-exist", Map.of(), "3"));
      final var result = repository.bulkUpdate(bulk);

      // then
      assertThat(result)
          .failsWithin(REQUEST_TIMEOUT)
          .withThrowableThat()
          .withRootCauseExactlyInstanceOf(ExporterException.class)
          .withMessageContaining("Failed to update 1 item(s)");
    }

    @Test
    void shouldBulkUpdateIncidents() throws PersistenceException, IOException {
      // given
      final var repository = createRepository();
      final var bulk = new IncidentBulkUpdate();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "1",
          new IncidentEntity()
              .setKey(1)
              .setState(IncidentState.PENDING)
              .setErrorMessage("failure"));
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "2",
          new IncidentEntity().setKey(2).setState(IncidentState.ACTIVE).setErrorMessage("failure"));
      batchRequest.executeWithRefresh();

      // when
      bulk.incidentRequests()
          .put(
              "1",
              new DocumentUpdate(
                  "1",
                  incidentTemplate.getFullQualifiedName(),
                  Map.of(IncidentTemplate.STATE, IncidentState.ACTIVE),
                  "1"));
      bulk.incidentRequests()
          .put(
              "2",
              new DocumentUpdate(
                  "2",
                  incidentTemplate.getFullQualifiedName(),
                  Map.of(IncidentTemplate.STATE, IncidentState.RESOLVED),
                  "1"));
      final var result = repository.bulkUpdate(bulk);

      // then
      assertThat(result).succeedsWithin(REQUEST_TIMEOUT);
      final var incidents =
          search(
              incidentTemplate.getFullQualifiedName(),
              IncidentTemplate.KEY,
              List.of("1", "2"),
              IncidentEntity.class);
      assertThat(incidents)
          .hasSize(2)
          .extracting(IncidentEntity::getKey, IncidentEntity::getState)
          .containsExactlyInAnyOrder(
              Tuple.tuple(1L, IncidentState.ACTIVE), Tuple.tuple(2L, IncidentState.RESOLVED));
    }

    @Test
    void shouldBulkUpdateListView() throws PersistenceException, IOException {
      // given
      final var repository = createRepository();
      final var bulk = new IncidentBulkUpdate();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          listViewTemplate.getFullQualifiedName(),
          "1",
          new ProcessInstanceForListViewEntity().setKey(1).setIncident(false));
      batchRequest.addWithId(
          listViewTemplate.getFullQualifiedName(),
          "2",
          new ProcessInstanceForListViewEntity().setKey(2).setIncident(true));
      batchRequest.executeWithRefresh();

      // when
      bulk.listViewRequests()
          .put(
              "1",
              new DocumentUpdate(
                  "1",
                  listViewTemplate.getFullQualifiedName(),
                  Map.of(ListViewTemplate.INCIDENT, true),
                  "1"));
      bulk.listViewRequests()
          .put(
              "2",
              new DocumentUpdate(
                  "2",
                  listViewTemplate.getFullQualifiedName(),
                  Map.of(ListViewTemplate.INCIDENT, false),
                  "2"));
      final var result = repository.bulkUpdate(bulk);

      // then
      assertThat(result).succeedsWithin(REQUEST_TIMEOUT);
      final var processInstances =
          search(
              listViewTemplate.getFullQualifiedName(),
              ListViewTemplate.PROCESS_INSTANCE_KEY,
              List.of("1", "2"),
              ProcessInstanceForListViewEntity.class);
      assertThat(processInstances)
          .hasSize(2)
          .extracting(
              ProcessInstanceForListViewEntity::getKey,
              ProcessInstanceForListViewEntity::isIncident)
          .containsExactlyInAnyOrder(Tuple.tuple(1L, true), Tuple.tuple(2L, false));
    }

    @Test
    void shouldBulkUpdateFlowNodeInstances() throws PersistenceException, IOException {
      // given
      final var repository = createRepository();
      final var bulk = new IncidentBulkUpdate();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          flowNodeInstanceTemplate.getFullQualifiedName(),
          "1",
          new FlowNodeInstanceEntity().setKey(1).setIncident(false));
      batchRequest.addWithId(
          flowNodeInstanceTemplate.getFullQualifiedName(),
          "2",
          new FlowNodeInstanceEntity().setKey(2).setIncident(false));
      batchRequest.executeWithRefresh();

      // when
      bulk.flowNodeInstanceRequests()
          .put(
              "1",
              new DocumentUpdate(
                  "1",
                  flowNodeInstanceTemplate.getFullQualifiedName(),
                  Map.of(FlowNodeInstanceTemplate.INCIDENT, true),
                  "1"));
      bulk.flowNodeInstanceRequests()
          .put(
              "2",
              new DocumentUpdate(
                  "2",
                  flowNodeInstanceTemplate.getFullQualifiedName(),
                  Map.of(FlowNodeInstanceTemplate.INCIDENT, false),
                  "2"));
      final var result = repository.bulkUpdate(bulk);

      // then
      assertThat(result).succeedsWithin(REQUEST_TIMEOUT);
      final var flowNodes =
          search(
              flowNodeInstanceTemplate.getFullQualifiedName(),
              FlowNodeInstanceTemplate.KEY,
              List.of("1", "2"),
              FlowNodeInstanceEntity.class);
      assertThat(flowNodes)
          .hasSize(2)
          .extracting(FlowNodeInstanceEntity::getKey, FlowNodeInstanceEntity::isIncident)
          .containsExactlyInAnyOrder(Tuple.tuple(1L, true), Tuple.tuple(2L, false));
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class AnalyzeTreePathIT {
    @Test
    void shouldAnalyzeTreePath() {
      // given
      final var repository = createRepository();
      final var treePath =
          new TreePath()
              .startTreePath(1)
              .appendFlowNode("call")
              .appendFlowNodeInstance(2)
              .appendProcessInstance(3)
              .appendFlowNode("task")
              .appendFlowNodeInstance(4)
              .toString();
      engineClient.createIndex(listViewTemplate, new IndexConfiguration());

      // when
      final var terms = repository.analyzeTreePath(treePath);

      // then
      assertThat(terms)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(String.class))
          .containsExactlyInAnyOrder(
              "PI_1",
              "PI_1/FN_call",
              "PI_1/FN_call/FNI_2",
              "PI_1/FN_call/FNI_2/PI_3",
              "PI_1/FN_call/FNI_2/PI_3/FN_task",
              "PI_1/FN_call/FNI_2/PI_3/FN_task/FNI_4");
    }

    @Test
    void shouldAnalyzeTreePathWhenHavingDatedIndices() {
      // given
      final var repository = createRepository();
      final var treePath =
          new TreePath()
              .startTreePath(1)
              .appendFlowNode("call")
              .appendFlowNodeInstance(2)
              .appendProcessInstance(3)
              .appendFlowNode("task")
              .appendFlowNodeInstance(4)
              .toString();
      engineClient.createIndex(listViewTemplate, new IndexConfiguration());
      engineClient.createIndex(createDatedIndex(listViewTemplate), new IndexConfiguration());

      // when
      final var terms = repository.analyzeTreePath(treePath);

      // then
      assertThat(terms)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.list(String.class))
          .containsExactlyInAnyOrder(
              "PI_1",
              "PI_1/FN_call",
              "PI_1/FN_call/FNI_2",
              "PI_1/FN_call/FNI_2/PI_3",
              "PI_1/FN_call/FNI_2/PI_3/FN_task",
              "PI_1/FN_call/FNI_2/PI_3/FN_task/FNI_4");
    }

    private IndexDescriptor createDatedIndex(final IndexDescriptor source) {
      final LocalDate date = LocalDate.now().minusDays(1);
      final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
      final String suffix = date.format(formatter);
      return new IndexDescriptor() {
        @Override
        public String getFullQualifiedName() {
          return source.getFullQualifiedName() + "-" + suffix;
        }

        @Override
        public String getAlias() {
          return source.getAlias();
        }

        @Override
        public String getIndexName() {
          return getFullQualifiedName() + "alias";
        }

        @Override
        public String getMappingsClasspathFilename() {
          return source.getMappingsClasspathFilename();
        }

        @Override
        public String getAllVersionsIndexNameRegexPattern() {
          return getFullQualifiedName() + "*";
        }

        @Override
        public String getIndexNameWithoutVersion() {
          return source.getIndexNameWithoutVersion();
        }

        @Override
        public String getVersion() {
          return source.getVersion();
        }
      };
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class WasProcessInstanceDeletedIT {
    @Test
    void shouldReturnNotDeletedIfDifferentKey() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(
          operationTemplate.getFullQualifiedName(),
          new OperationEntity()
              .setProcessInstanceKey(2L)
              .setType(OperationType.DELETE_PROCESS_INSTANCE)
              .setState(OperationState.COMPLETED));
      batchRequest.executeWithRefresh();

      // when
      final var wasDeleted = repository.wasProcessInstanceDeleted(1L);

      // then
      assertThat(wasDeleted).succeedsWithin(REQUEST_TIMEOUT).isEqualTo(false);
    }

    @Test
    void shouldReturnNotDeletedIfDifferentType() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(
          operationTemplate.getFullQualifiedName(),
          new OperationEntity()
              .setProcessInstanceKey(2L)
              .setType(OperationType.CANCEL_PROCESS_INSTANCE)
              .setState(OperationState.COMPLETED));
      batchRequest.executeWithRefresh();

      // when
      final var wasDeleted = repository.wasProcessInstanceDeleted(1L);

      // then
      assertThat(wasDeleted).succeedsWithin(REQUEST_TIMEOUT).isEqualTo(false);
    }

    @ParameterizedTest
    @EnumSource(
        value = OperationState.class,
        names = {"SENT", "COMPLETED"},
        mode = Mode.EXCLUDE)
    void shouldReturnNotDeletedIfDifferentState(final OperationState state)
        throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(
          operationTemplate.getFullQualifiedName(),
          new OperationEntity()
              .setProcessInstanceKey(1L)
              .setType(OperationType.DELETE_PROCESS_INSTANCE)
              .setState(state));
      batchRequest.executeWithRefresh();

      // when
      final var wasDeleted = repository.wasProcessInstanceDeleted(1L);

      // then
      assertThat(wasDeleted).succeedsWithin(REQUEST_TIMEOUT).isEqualTo(false);
    }

    @ParameterizedTest
    @EnumSource(
        value = OperationState.class,
        names = {"SENT", "COMPLETED"},
        mode = Mode.INCLUDE)
    void shouldReturnDeleted(final OperationState state) throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.add(
          operationTemplate.getFullQualifiedName(),
          new OperationEntity()
              .setProcessInstanceKey(1L)
              .setType(OperationType.DELETE_PROCESS_INSTANCE)
              .setState(state));
      batchRequest.executeWithRefresh();

      // when
      final var wasDeleted = repository.wasProcessInstanceDeleted(1L);

      // then
      assertThat(wasDeleted).succeedsWithin(REQUEST_TIMEOUT).isEqualTo(true);
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class GetFlowNodesInListViewIT {
    @Test
    void shouldGetFlowNodes() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithRouting(
          listViewTemplate.getFullQualifiedName(),
          createFlowNodeInstance(ListViewTemplate.ACTIVITIES_JOIN_RELATION).setId("1"),
          "0");
      batchRequest.addWithRouting(
          listViewTemplate.getFullQualifiedName(),
          createFlowNodeInstance(ListViewTemplate.ACTIVITIES_JOIN_RELATION).setId("2"),
          "0");
      batchRequest.executeWithRefresh();

      // when
      final var flowNodes = repository.getFlowNodesInListView(List.of("1", "2"));

      // then
      assertThat(flowNodes)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(Document.class))
          .containsExactlyInAnyOrder(
              new Document("1", listViewTemplate.getFullQualifiedName()),
              new Document("2", listViewTemplate.getFullQualifiedName()));
    }

    @Test
    void shouldNotGetNonFlowNodes() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithRouting(
          listViewTemplate.getFullQualifiedName(),
          createFlowNodeInstance(ListViewTemplate.ACTIVITIES_JOIN_RELATION).setId("1"),
          "0");
      batchRequest.addWithRouting(
          listViewTemplate.getFullQualifiedName(),
          createFlowNodeInstance(ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION).setId("2"),
          "0");
      batchRequest.executeWithRefresh();

      // when
      final var flowNodes = repository.getFlowNodesInListView(List.of("1", "2"));

      // then
      assertThat(flowNodes)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(Document.class))
          .containsExactly(new Document("1", listViewTemplate.getFullQualifiedName()));
    }

    @Test
    void shouldNotGetFlowNodesWithOtherKeys() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithRouting(
          listViewTemplate.getFullQualifiedName(),
          createFlowNodeInstance(ListViewTemplate.ACTIVITIES_JOIN_RELATION).setId("1"),
          "0");
      batchRequest.addWithRouting(
          listViewTemplate.getFullQualifiedName(),
          createFlowNodeInstance(ListViewTemplate.ACTIVITIES_JOIN_RELATION).setId("2"),
          "0");
      batchRequest.executeWithRefresh();

      // when
      final var flowNodes = repository.getFlowNodesInListView(List.of("1"));

      // then
      assertThat(flowNodes)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(Document.class))
          .containsExactly(new Document("1", listViewTemplate.getFullQualifiedName()));
    }

    private FlowNodeInstanceForListViewEntity createFlowNodeInstance(final String joinRelation) {
      final var entity = new FlowNodeInstanceForListViewEntity();
      entity.setJoinRelation(new ListViewJoinRelation(joinRelation).setParent(0L));
      return entity;
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class GetFlowNodeInstancesIT {
    @Test
    void shouldGetFlowNodes() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          flowNodeInstanceTemplate.getFullQualifiedName(), "1", new FlowNodeInstanceEntity());
      batchRequest.addWithId(
          flowNodeInstanceTemplate.getFullQualifiedName(), "2", new FlowNodeInstanceEntity());
      batchRequest.executeWithRefresh();

      // when
      final var flowNodes = repository.getFlowNodeInstances(List.of("1", "2"));

      // then
      assertThat(flowNodes)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(Document.class))
          .containsExactlyInAnyOrder(
              new Document("1", flowNodeInstanceTemplate.getFullQualifiedName()),
              new Document("2", flowNodeInstanceTemplate.getFullQualifiedName()));
    }

    @Test
    void shouldNotGetFlowNodesWithOtherKeys() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          flowNodeInstanceTemplate.getFullQualifiedName(), "1", new FlowNodeInstanceEntity());
      batchRequest.addWithId(
          flowNodeInstanceTemplate.getFullQualifiedName(), "2", new FlowNodeInstanceEntity());
      batchRequest.executeWithRefresh();

      // when
      final var flowNodes = repository.getFlowNodeInstances(List.of("1"));

      // then
      assertThat(flowNodes)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(Document.class))
          .containsExactly(new Document("1", flowNodeInstanceTemplate.getFullQualifiedName()));
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class GetActiveIncidentsTest {
    @Test
    void shouldGetActiveIncidentsWithSharedPath() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "7",
          newIncident(7).setState(IncidentState.ACTIVE).setTreePath("PI_1/FNI_2"));
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "8",
          newIncident(8).setState(IncidentState.ACTIVE).setTreePath("PI_1/FNI_2/PI_3/FNI_4"));
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "9",
          newIncident(9).setState(IncidentState.ACTIVE).setTreePath("PI_5/FNI_6"));
      batchRequest.executeWithRefresh();

      // when
      final var incidents = repository.getActiveIncidentsByTreePaths(List.of("PI_1"));

      // then
      assertThat(incidents)
          .succeedsWithin(Duration.ofSeconds(10))
          .asInstanceOf(InstanceOfAssertFactories.collection(ActiveIncident.class))
          .containsExactlyInAnyOrder(
              new ActiveIncident("8", "PI_1/FNI_2/PI_3/FNI_4"),
              new ActiveIncident("7", "PI_1/FNI_2"));
    }

    @Test
    void shouldNotReturnInactiveIncidents() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "7",
          newIncident(7).setState(IncidentState.ACTIVE).setTreePath("PI_1/FNI_2"));
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "8",
          newIncident(8).setState(IncidentState.PENDING).setTreePath("PI_1/FNI_3"));
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "9",
          newIncident(9).setState(IncidentState.RESOLVED).setTreePath("PI_1/FNI_4"));
      batchRequest.executeWithRefresh();

      // when
      final var incidents = repository.getActiveIncidentsByTreePaths(List.of("PI_1"));

      // then
      assertThat(incidents)
          .succeedsWithin(Duration.ofSeconds(10))
          .asInstanceOf(InstanceOfAssertFactories.collection(ActiveIncident.class))
          .containsExactlyInAnyOrder(new ActiveIncident("7", "PI_1/FNI_2"));
    }

    @Test
    void shouldGetActiveIncidents() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "7",
          newIncident(7).setState(IncidentState.ACTIVE).setTreePath("PI_1/FNI_2/PI_7"));
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "8",
          newIncident(8).setState(IncidentState.ACTIVE).setTreePath("PI_3/FNI_4"));
      batchRequest.addWithId(
          incidentTemplate.getFullQualifiedName(),
          "9",
          newIncident(9).setState(IncidentState.ACTIVE).setTreePath("PI_5/FNI_6"));
      batchRequest.executeWithRefresh();

      // when
      final var incidents =
          repository.getActiveIncidentsByTreePaths(List.of("PI_1/FNI_2", "PI_3/FNI_4"));

      // then
      assertThat(incidents)
          .succeedsWithin(Duration.ofSeconds(10))
          .asInstanceOf(InstanceOfAssertFactories.collection(ActiveIncident.class))
          .containsExactlyInAnyOrder(
              new ActiveIncident("8", "PI_3/FNI_4"), new ActiveIncident("7", "PI_1/FNI_2/PI_7"));
    }
  }

  @DisabledIfSystemProperty(
      named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
      matches = "^(?=\\s*\\S).*$",
      disabledReason = "Excluding from AWS OS IT CI")
  @Nested
  final class GetProcessInstancesIT {
    @Test
    void shouldGetProcessInstances() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          listViewTemplate.getFullQualifiedName(),
          "1",
          new ProcessInstanceForListViewEntity().setKey(1).setTreePath("PI_1"));
      batchRequest.addWithId(
          listViewTemplate.getFullQualifiedName(),
          "2",
          new ProcessInstanceForListViewEntity().setKey(2).setTreePath("PI_2"));
      batchRequest.executeWithRefresh();

      // when
      final var processInstances = repository.getProcessInstances(List.of("1", "2"));

      // then
      assertThat(processInstances)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(ProcessInstanceDocument.class))
          .containsExactlyInAnyOrder(
              new ProcessInstanceDocument("1", listViewTemplate.getFullQualifiedName(), 1, "PI_1"),
              new ProcessInstanceDocument("2", listViewTemplate.getFullQualifiedName(), 2, "PI_2"));
    }

    @Test
    void shouldNotGetProcessInstancesWithOtherKeys() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      batchRequest.addWithId(
          listViewTemplate.getFullQualifiedName(),
          "1",
          new ProcessInstanceForListViewEntity().setKey(1).setTreePath("PI_1"));
      batchRequest.addWithId(
          listViewTemplate.getFullQualifiedName(),
          "2",
          new ProcessInstanceForListViewEntity().setKey(2).setTreePath("PI_2"));
      batchRequest.executeWithRefresh();

      // when
      final var processInstances = repository.getProcessInstances(List.of("1"));

      // then
      assertThat(processInstances)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(ProcessInstanceDocument.class))
          .containsExactly(
              new ProcessInstanceDocument("1", listViewTemplate.getFullQualifiedName(), 1, "PI_1"));
    }

    @Test
    void shouldNotGetWrongJoinRelation() throws PersistenceException {
      // given
      final var repository = createRepository();
      final var batchRequest = clientAdapter.createBatchRequest();
      final var flowNode = new FlowNodeInstanceForListViewEntity().setKey(2).setId("2");
      flowNode.getJoinRelation().setParent(1L);
      batchRequest.addWithId(
          listViewTemplate.getFullQualifiedName(),
          "1",
          new ProcessInstanceForListViewEntity().setKey(1).setTreePath("PI_1"));
      batchRequest.addWithRouting(listViewTemplate.getFullQualifiedName(), flowNode, "1");
      batchRequest.executeWithRefresh();

      // when
      final var processInstances = repository.getProcessInstances(List.of("1", "2"));

      // then
      assertThat(processInstances)
          .succeedsWithin(REQUEST_TIMEOUT)
          .asInstanceOf(InstanceOfAssertFactories.collection(ProcessInstanceDocument.class))
          .containsExactly(
              new ProcessInstanceDocument("1", listViewTemplate.getFullQualifiedName(), 1, "PI_1"));
    }
  }
}
