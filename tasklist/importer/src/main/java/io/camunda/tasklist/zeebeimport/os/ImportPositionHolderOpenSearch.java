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
package io.camunda.tasklist.zeebeimport.os;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.util.Either;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.zeebeimport.ImportPositionHolder;
import io.camunda.tasklist.zeebeimport.ImportPositionHolderAbstract;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
@Conditional(OpenSearchCondition.class)
public class ImportPositionHolderOpenSearch extends ImportPositionHolderAbstract
    implements ImportPositionHolder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ImportPositionHolderOpenSearch.class);

  // this is the in-memory only storage

  @Qualifier("zeebeOsClient")
  @Autowired
  private OpenSearchClient osClient;

  public ImportPositionEntity getLatestLoadedPosition(String aliasTemplate, int partitionId)
      throws IOException {
    final Query query =
        OpenSearchUtil.joinWithAnd(
            new Query.Builder()
                .term(
                    t ->
                        t.field(ImportPositionIndex.ALIAS_NAME)
                            .value(FieldValue.of(aliasTemplate))),
            new Query.Builder()
                .term(
                    t ->
                        t.field(ImportPositionIndex.PARTITION_ID)
                            .value(FieldValue.of(partitionId))));

    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .query(query)
            .size(10)
            .index(List.of(importPositionType.getAlias()))
            .build();

    final SearchResponse<ImportPositionEntity> searchResponse =
        osClient.search(searchRequest, ImportPositionEntity.class);

    final List<Hit<ImportPositionEntity>> hits = searchResponse.hits().hits();

    ImportPositionEntity position =
        new ImportPositionEntity().setAliasName(aliasTemplate).setPartitionId(partitionId);

    for (Hit<ImportPositionEntity> hit : hits) {
      position = hit.source();
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
            OpenSearchUtil.processBulkRequest(osClient, bulkRequest);
            return null;
          });

      return Either.right(true);
    } catch (final Throwable e) {
      LOGGER.error("Error occurred while persisting latest loaded position", e);
      return Either.left(e);
    }
  }

  private Either<Exception, BulkRequest> prepareBulkRequest(
      final Map<String, ImportPositionEntity> positions) {
    final var bulkRequest = new BulkRequest.Builder();
    final ArrayList<BulkOperation> ops = new ArrayList<>();

    if (positions.size() > 0) {
      final var preparedUpdateRequests =
          positions.values().stream()
              .map(this::prepareUpdateRequest)
              .collect(Either.collectorFoldingLeft());

      if (preparedUpdateRequests.isLeft()) {
        final var e = preparedUpdateRequests.getLeft();
        return Either.left(e);
      }

      preparedUpdateRequests
          .get()
          .forEach(
              p -> {
                ops.add(
                    new BulkOperation.Builder()
                        .index(
                            IndexOperation.of(
                                io ->
                                    io.index(importPositionType.getFullQualifiedName())
                                        .document(p.doc())
                                        .id(p.id())))
                        .build());
              });
    }

    return Either.right(bulkRequest.operations(ops).build());
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
          new UpdateRequest.Builder()
              .index(index)
              .id(position.getId())
              .upsert(CommonUtils.getJsonObjectFromEntity(updateFields))
              .doc(CommonUtils.getJsonObjectFromEntity(position))
              .docAsUpsert(true)
              .build();

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
}
