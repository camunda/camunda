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
package io.camunda.tasklist.zeebeimport.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.zeebeimport.ImportPositionHolder;
import io.camunda.tasklist.zeebeimport.ImportPositionHolderAbstract;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
@Conditional(ElasticSearchCondition.class)
public class ImportPositionHolderElasticSearch extends ImportPositionHolderAbstract
    implements ImportPositionHolder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ImportPositionHolderElasticSearch.class);

  // this is the in-memory only storage

  @Autowired private RestHighLevelClient esClient;

  public ImportPositionEntity getLatestLoadedPosition(String aliasTemplate, int partitionId)
      throws IOException {
    final QueryBuilder queryBuilder =
        joinWithAnd(
            termQuery(ImportPositionIndex.ALIAS_NAME, aliasTemplate),
            termQuery(ImportPositionIndex.PARTITION_ID, partitionId));

    final SearchRequest searchRequest =
        new SearchRequest(importPositionType.getAlias())
            .source(new SearchSourceBuilder().query(queryBuilder).size(10));

    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final Iterator<SearchHit> hitIterator = searchResponse.getHits().iterator();

    ImportPositionEntity position =
        new ImportPositionEntity().setAliasName(aliasTemplate).setPartitionId(partitionId);

    if (hitIterator.hasNext()) {
      position =
          ElasticsearchUtil.fromSearchHit(
              hitIterator.next().getSourceAsString(), objectMapper, ImportPositionEntity.class);
    }
    LOGGER.debug(
        "Latest loaded position for alias [{}] and partitionId [{}]: {}",
        aliasTemplate,
        partitionId,
        position);

    return position;
  }

  public Either<Throwable, Boolean> updateImportPositions(
      final Map<String, ImportPositionEntity> positions) {
    final var preparedBulkRequest = prepareBulkRequest(positions);

    if (preparedBulkRequest.isLeft()) {
      final var e = preparedBulkRequest.getLeft();
      return Either.left(e);
    }

    try {
      final var bulkRequest = preparedBulkRequest.get();

      withImportPositionTimer(
          () -> {
            ElasticsearchUtil.processBulkRequest(esClient, bulkRequest);
            return null;
          });

      return Either.right(true);
    } catch (final Throwable e) {
      LOGGER.error("Error occurred while persisting latest loaded position", e);
      return Either.left(e);
    }
  }

  private Either<Exception, UpdateRequest> prepareUpdateRequest(
      final ImportPositionEntity position) {
    try {
      final var index = importPositionType.getFullQualifiedName();
      final var source = objectMapper.writeValueAsString(position);
      final var updateFields = new HashMap<String, Object>();

      updateFields.put(ImportPositionIndex.POSITION, position.getPosition());
      updateFields.put(ImportPositionIndex.FIELD_INDEX_NAME, position.getIndexName());
      updateFields.put(ImportPositionIndex.SEQUENCE, position.getSequence());

      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(index)
              .id(position.getId())
              .upsert(source, XContentType.JSON)
              .doc(updateFields);

      return Either.right(updateRequest);

    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Error occurred while preparing request to update processed position for %s",
              position.getAliasName()),
          e);
      return Either.left(e);
    }
  }

  private Either<Exception, org.elasticsearch.action.bulk.BulkRequest> prepareBulkRequest(
      final Map<String, ImportPositionEntity> positions) {
    final var bulkRequest = new BulkRequest();

    if (positions.size() > 0) {
      final var preparedUpdateRequests =
          positions.values().stream()
              .map(this::prepareUpdateRequest)
              .collect(Either.collectorFoldingLeft());

      if (preparedUpdateRequests.isLeft()) {
        final var e = preparedUpdateRequests.getLeft();
        return Either.left(e);
      }

      preparedUpdateRequests.get().forEach(bulkRequest::add);
    }

    return Either.right(bulkRequest);
  }
}
