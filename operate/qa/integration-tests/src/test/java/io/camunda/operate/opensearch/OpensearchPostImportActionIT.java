/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OpensearchConnector;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.IndexSchemaValidator;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.indices.IndexDescriptor;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.SearchClientTestHelper;
import io.camunda.operate.schema.util.TestIndex;
import io.camunda.operate.schema.util.opensearch.OpenSearchClientTestHelper;
import io.camunda.operate.schema.util.opensearch.OpenSearchSchemaTestHelper;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.opensearch.OpensearchTaskStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.TreePath;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.zeebeimport.post.PostImportAction;
import io.camunda.operate.zeebeimport.post.opensearch.OpensearchIncidentPostImportAction;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {
      IncidentTemplate.class,
      OperationTemplate.class,
      ListViewTemplate.class,
      FlowNodeInstanceTemplate.class,
      PostImporterQueueTemplate.class,
      TestIndex.class,
      OpensearchSchemaManager.class,
      OpenSearchSchemaTestHelper.class,
      OpensearchTaskStore.class,
      IndexSchemaValidator.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      OpensearchConnector.class,
      RichOpenSearchClient.class,
      ElasticsearchTaskStore.class,
      DatabaseInfo.class,
      OpenSearchSchemaTestHelper.class,
      OpenSearchClientTestHelper.class,
      OpensearchIncidentPostImportAction.class
    },
    properties = {"spring.profiles.active=", OperateProperties.PREFIX + ".database=opensearch"})
@EnableConfigurationProperties(OperateProperties.class)
public class OpensearchPostImportActionIT extends AbstractOpensearchConnectorProxyIT {

  private static final int PARTITION_ID = 1;

  @Autowired protected SchemaManager schemaManager;
  @Autowired protected SchemaTestHelper schemaTestHelper;
  @Autowired protected SearchClientTestHelper searchClientTestHelper;
  @Autowired protected BeanFactory beanFactory;

  @Autowired protected ListViewTemplate listViewTemplate;
  @Autowired protected FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  @Autowired protected PostImporterQueueTemplate postImporterQueueTemplate;
  @Autowired protected IncidentTemplate incidentTemplate;

  // not needed for the test but to satisfy our auto wirings
  @MockitoBean("postImportThreadPoolScheduler")
  protected ThreadPoolTaskScheduler postImporterScheduler;

  @MockitoBean protected ImportPositionHolder importPositionHolder;

  protected PostImportAction postImportAction;

  @BeforeEach
  public void setUp() throws IOException {
    postImportAction = beanFactory.getBean(PostImportAction.class, PARTITION_ID);

    final ImportPositionEntity startPosition = new ImportPositionEntity();
    startPosition.setPostImporterPosition(0L);
    when(importPositionHolder.getLatestLoadedPosition(any(), anyInt())).thenReturn(startPosition);
  }

  @AfterEach
  public void tearDown() {

    schemaTestHelper.dropSchema();
  }

  private void createProcessInstance(String key, Consumer<Map<String, Object>> propertiesCreator) {

    final Map<String, Object> processInstance = new HashMap<String, Object>();
    propertiesCreator.accept(processInstance);

    final Map<String, Object> joinRelation = new HashMap<String, Object>();
    joinRelation.put("name", ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);

    processInstance.put(ListViewTemplate.JOIN_RELATION, joinRelation);

    searchClientTestHelper.createDocument(
        listViewTemplate.getFullQualifiedName(), key, key, processInstance);
  }

  private void createFlowNodeInstance(
      String key,
      String processInstanceKey,
      Consumer<Map<String, Object>> listViewPropertiesCreator,
      Consumer<Map<String, Object>> flowNodeInstancePropertiesCreator) {

    final Map<String, Object> listViewFlowNodeInstance = new HashMap<String, Object>();
    listViewPropertiesCreator.accept(listViewFlowNodeInstance);

    final Map<String, Object> joinRelation = new HashMap<String, Object>();
    joinRelation.put("name", ListViewTemplate.ACTIVITIES_JOIN_RELATION);
    joinRelation.put("parent", Long.valueOf(processInstanceKey));

    listViewFlowNodeInstance.put(ListViewTemplate.JOIN_RELATION, joinRelation);

    searchClientTestHelper.createDocument(
        listViewTemplate.getFullQualifiedName(), key, processInstanceKey, listViewFlowNodeInstance);

    final Map<String, Object> flowNodeInstance = new HashMap<String, Object>();
    flowNodeInstancePropertiesCreator.accept(flowNodeInstance);

    searchClientTestHelper.createDocument(
        flowNodeInstanceTemplate.getFullQualifiedName(), key, flowNodeInstance);
  }

  @Test
  public void shouldUseCorrectRoutingValueWhenUpdatingFlowNodeInstance()
      throws IOException, InterruptedException {

    // given
    schemaManager.createSchema();

    final String callingProcessInstanceKey = "1";
    createProcessInstance(callingProcessInstanceKey, map -> {});

    final String callingFlowNodeInstanceKey = "2";
    createFlowNodeInstance(
        callingFlowNodeInstanceKey, callingProcessInstanceKey, map -> {}, map -> {});

    final String calledProcessInstanceKey = "3";
    final TreePath calledProcessInstanceTreePath =
        new TreePath()
            .appendProcessInstance(callingProcessInstanceKey)
            .appendEntries("fooActivityId", callingFlowNodeInstanceKey, calledProcessInstanceKey);
    createProcessInstance(
        calledProcessInstanceKey,
        map -> {
          map.put("treePath", calledProcessInstanceTreePath.toString());
        });

    final String calledFlowNodeInstanceKey = "4";
    createFlowNodeInstance(
        calledFlowNodeInstanceKey, calledProcessInstanceKey, map -> {}, map -> {});

    // must be above Integer.MAX_VALUE as the post importer assumes a long value
    final Long incidentKey = 25_000_000_000L;
    final String postImporterQueueKey = "1";
    final Map<String, Object> postImporterQueueEntry = new HashMap<String, Object>();
    postImporterQueueEntry.put("key", incidentKey);
    postImporterQueueEntry.put("position", 1L);
    postImporterQueueEntry.put("actionType", PostImporterActionType.INCIDENT);
    postImporterQueueEntry.put("intent", IncidentIntent.CREATED);
    postImporterQueueEntry.put("partitionId", PARTITION_ID);

    searchClientTestHelper.createDocument(
        postImporterQueueTemplate.getFullQualifiedName(),
        postImporterQueueKey,
        postImporterQueueEntry);

    final String incidentFlowNodeId = "incidentActivity";

    final Map<String, Object> incidentEntry = new HashMap<String, Object>();
    incidentEntry.put("key", incidentKey);
    incidentEntry.put("id", String.valueOf(incidentKey));
    incidentEntry.put("processInstanceKey", Long.valueOf(calledProcessInstanceKey));
    incidentEntry.put("flowNodeId", incidentFlowNodeId);
    incidentEntry.put("flowNodeInstanceKey", calledFlowNodeInstanceKey);
    incidentEntry.put("state", IncidentState.ACTIVE);

    final TreePath incidentTreePath =
        calledProcessInstanceTreePath
            .appendFlowNode(incidentFlowNodeId)
            .appendFlowNodeInstance(calledFlowNodeInstanceKey);
    incidentEntry.put("treePath", incidentTreePath.toString());

    searchClientTestHelper.createDocument(
        incidentTemplate.getFullQualifiedName(), incidentKey.toString(), incidentEntry);

    // refresh so that the post importer sees everything
    searchClientTestHelper.refreshAllIndexes();

    // reset wiremock so that no prior requests are considered for verification
    WIRE_MOCK_SERVER.resetRequests();

    // when
    postImportAction.performOneRound();

    // then
    final List<LoggedRequest> requestsSent =
        WIRE_MOCK_SERVER
            .findRequestsMatching(
                WireMock.postRequestedFor(WireMock.urlMatching("/_bulk.*")).build())
            .getRequests();

    assertThat(requestsSent).hasSize(1);

    final LoggedRequest bulkRequest = requestsSent.get(0);
    final List<JsonNode> bulkActions = parseActions(bulkRequest.getBodyAsString());

    // the update for the calling flow node instance was correctly routed to the calling process
    // instance
    assertUpdateWasRoutedTo(
        bulkActions, listViewTemplate, callingFlowNodeInstanceKey, callingProcessInstanceKey);
    assertUpdateWasRoutedTo(
        bulkActions, flowNodeInstanceTemplate, callingFlowNodeInstanceKey, null);

    // and the update for the called flow node instance was correctly routed to the called process
    // instance
    assertUpdateWasRoutedTo(
        bulkActions, listViewTemplate, calledFlowNodeInstanceKey, calledProcessInstanceKey);
    assertUpdateWasRoutedTo(bulkActions, flowNodeInstanceTemplate, calledFlowNodeInstanceKey, null);
  }

  private void assertUpdateWasRoutedTo(
      List<JsonNode> bulkActions,
      IndexDescriptor index,
      String documentId,
      String expectedRountingKey) {
    final List<JsonNode> updatesForDocument =
        filterUpdatesToIndexAndDocument(bulkActions, index, documentId);

    assertThat(updatesForDocument).hasSize(1);

    final JsonNode update = updatesForDocument.get(0);
    if (expectedRountingKey != null) {
      assertThat(update.has("routing")).isTrue();

      final String actualRoutingKey = update.get("routing").asText();
      assertThat(actualRoutingKey).isEqualTo(expectedRountingKey);
    } else {
      assertThat(update.has("routing")).isFalse();
    }
  }

  private List<JsonNode> filterUpdatesToIndexAndDocument(
      List<JsonNode> bulkActions, IndexDescriptor index, String documentId) {
    return bulkActions.stream()
        .filter(n -> n.has("update")) // only updates
        .map(n -> n.get("update"))
        .filter(
            n ->
                index.getFullQualifiedName().equals(n.get("_index").asText())
                    && documentId.equals(n.get("_id").asText()))
        .collect(Collectors.toList());
  }

  private List<JsonNode> parseActions(String bulkRequestBody) throws IOException {
    final ObjectMapper objectMapper = new ObjectMapper();
    final ObjectReader reader = objectMapper.readerFor(JsonNode.class);

    final MappingIterator<JsonNode> iterator = reader.readValues(bulkRequestBody);

    // bulk requests are a list of JSON objects in the form
    // <action>
    // <document>
    // as we are only interested in the actions, we skip every second result

    final List<JsonNode> actions = new ArrayList<JsonNode>();

    while (iterator.hasNext()) {
      // we assume here that the request body is conform to what ES expects (i.e. no odd number of
      // json objects)
      final JsonNode action = iterator.next();
      actions.add(action);

      iterator.next(); // skipping the document
    }

    return actions;
  }
}
