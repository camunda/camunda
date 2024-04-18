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
package io.camunda.operate.schema.migration.elasticsearch;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static io.camunda.operate.util.LambdaExceptionUtil.rethrowConsumer;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.MigrationException;
import io.camunda.operate.property.MigrationProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.migration.FillPostImporterQueuePlan;
import io.camunda.operate.schema.migration.Step;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchFillPostImporterQueuePlan implements FillPostImporterQueuePlan {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchFillPostImporterQueuePlan.class);

  @Autowired private OperateProperties operateProperties;

  @Autowired private MigrationProperties migrationProperties;

  @Autowired private ObjectMapper objectMapper;
  @Autowired private RestHighLevelClient esClient;

  private Long flowNodesWithIncidentsCount;
  private List<Step> steps;

  private String listViewIndexName;
  private String incidentsIndexName;
  private String postImporterQueueIndexName;

  @Override
  public FillPostImporterQueuePlan setListViewIndexName(String listViewIndexName) {
    this.listViewIndexName = listViewIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setIncidentsIndexName(String incidentsIndexName) {
    this.incidentsIndexName = incidentsIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setPostImporterQueueIndexName(
      String postImporterQueueIndexName) {
    this.postImporterQueueIndexName = postImporterQueueIndexName;
    return this;
  }

  @Override
  public FillPostImporterQueuePlan setSteps(List<Step> steps) {
    this.steps = steps;
    return this;
  }

  @Override
  public List<Step> getSteps() {
    return steps;
  }

  @Override
  public void executeOn(final SchemaManager schemaManager) throws MigrationException {
    final long srcCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (srcCount > 0) {
      LOGGER.info("No migration needed for postImporterQueueIndex, already contains data.");
      return;
    }

    // iterate over flow node instances with pending incidents
    final String incidentKeysFieldName = "incidentKeys";
    final SearchRequest searchRequest =
        new SearchRequest(listViewIndexName + "*")
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
                            termQuery("pendingIncident", true)))
                    .fetchSource(incidentKeysFieldName, null)
                    .sort(ListViewTemplate.ID)
                    .size(operateProperties.getElasticsearch().getBatchSize()));

    try {
      scroll(
          searchRequest,
          rethrowConsumer(
              hits -> {
                if (flowNodesWithIncidentsCount == null) {
                  flowNodesWithIncidentsCount = hits.getTotalHits().value;
                }
                final List<IncidentEntity> incidents =
                    getIncidentEntities(incidentKeysFieldName, esClient, hits);
                final BulkRequest bulkRequest = new BulkRequest();
                final int[] index = {0};
                for (IncidentEntity incident : incidents) {
                  index[0]++;
                  final PostImporterQueueEntity entity =
                      createPostImporterQueueEntity(incident, index[0]);
                  bulkRequest.add(
                      new IndexRequest()
                          .index(postImporterQueueIndexName)
                          .source(objectMapper.writeValueAsString(entity), XContentType.JSON));
                }
                ElasticsearchUtil.processBulkRequest(
                    esClient,
                    bulkRequest,
                    operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes(),
                    operateProperties.getElasticsearch().isBulkRequestIgnoreNullIndex());
              }),
          esClient,
          migrationProperties.getScrollKeepAlive());
    } catch (Exception e) {
      throw new MigrationException(e.getMessage(), e);
    }
  }

  @Override
  public void validateMigrationResults(final SchemaManager schemaManager)
      throws MigrationException {
    final long dstCount = schemaManager.getNumberOfDocumentsFor(postImporterQueueIndexName);
    if (flowNodesWithIncidentsCount != null && flowNodesWithIncidentsCount > dstCount) {
      throw new MigrationException(
          String.format(
              "Exception occurred when migrating %s. Number of flow nodes with pending incidents: %s, number of documents in post-importer-queue: %s",
              postImporterQueueIndexName, flowNodesWithIncidentsCount, dstCount));
    }
  }

  private List<IncidentEntity> getIncidentEntities(
      String incidentKeysFieldName, RestHighLevelClient esClient, SearchHits hits)
      throws IOException {
    final List<Long> incidentKeys =
        Arrays.stream(hits.getHits())
            .map(sh -> (List<Long>) sh.getSourceAsMap().get(incidentKeysFieldName))
            .flatMap(List::stream)
            .collect(Collectors.toList());
    final SearchRequest incidentSearchRequest =
        new SearchRequest(incidentsIndexName + "*")
            .source(
                new SearchSourceBuilder()
                    .query(termsQuery(IncidentTemplate.ID, incidentKeys))
                    .sort(IncidentTemplate.ID)
                    .size(operateProperties.getElasticsearch().getBatchSize()));

    final SearchResponse incidentsResponse =
        esClient.search(incidentSearchRequest, RequestOptions.DEFAULT);
    final List<IncidentEntity> incidents =
        ElasticsearchUtil.mapSearchHits(
            incidentsResponse.getHits().getHits(), objectMapper, IncidentEntity.class);
    return incidents;
  }

  private PostImporterQueueEntity createPostImporterQueueEntity(
      IncidentEntity incident, long index) {
    return new PostImporterQueueEntity()
        .setId(String.format("%s-%s", incident.getId(), incident.getState().getZeebeIntent()))
        .setCreationTime(OffsetDateTime.now())
        .setKey(incident.getKey())
        .setIntent(incident.getState().getZeebeIntent())
        .setPosition(index)
        .setPartitionId(incident.getPartitionId())
        .setActionType(PostImporterActionType.INCIDENT)
        .setProcessInstanceKey(incident.getProcessInstanceKey());
  }

  @Override
  public String toString() {
    return "ElasticsearchFillPostImporterQueuePlan{"
        + "listViewIndexName='"
        + listViewIndexName
        + '\''
        + ", incidentsIndexName='"
        + incidentsIndexName
        + '\''
        + ", postImporterQueueIndexName='"
        + postImporterQueueIndexName
        + '\''
        + ", operateProperties="
        + operateProperties
        + ", migrationProperties="
        + migrationProperties
        + ", objectMapper="
        + objectMapper
        + ", flowNodesWithIncidentsCount="
        + flowNodesWithIncidentsCount
        + '}';
  }
}
