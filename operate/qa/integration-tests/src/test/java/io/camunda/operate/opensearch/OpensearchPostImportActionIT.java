/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.SearchClientTestHelper;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      IncidentTemplate.class,
      OperationTemplate.class,
      ListViewTemplate.class,
      FlowNodeInstanceTemplate.class,
      PostImporterQueueTemplate.class,
      UserIndex.class,
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
  @MockBean(name = "postImportThreadPoolScheduler")
  protected ThreadPoolTaskScheduler postImporterScheduler;

  @MockBean protected ImportPositionHolder importPositionHolder;

  protected PostImportAction postImportAction;

  @Before
  public void setUp() throws IOException {
    postImportAction = beanFactory.getBean(PostImportAction.class, PARTITION_ID);

    final ImportPositionEntity startPosition = new ImportPositionEntity();
    startPosition.setPostImporterPosition(0L);
    when(importPositionHolder.getLatestLoadedPosition(any(), anyInt())).thenReturn(startPosition);
  }

  @After
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
