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
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.store.ImportStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.Either;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchImportStore implements ImportStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchImportStore.class);
  @Autowired private ImportPositionIndex importPositionType;
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private Metrics metrics;

  @Autowired private OperateProperties operateProperties;

  @Override
  public ImportPositionEntity getImportPositionByAliasAndPartitionId(
      final String alias, final int partitionId) throws IOException {
    final var searchRequestBuilder =
        searchRequestBuilder(importPositionType.getAlias())
            .size(10)
            .query(
                and(
                    term(ImportPositionIndex.ALIAS_NAME, alias),
                    term(ImportPositionIndex.PARTITION_ID, partitionId)));

    final var response =
        richOpenSearchClient.doc().search(searchRequestBuilder, ImportPositionEntity.class);

    ImportPositionEntity importPositionEntity = new ImportPositionEntity();
    if (!response.hits().hits().isEmpty()) {
      importPositionEntity = response.hits().hits().get(0).source();
    }
    LOGGER.debug(
        "Latest loaded position for alias [{}] and partitionId [{}]: {}",
        alias,
        partitionId,
        importPositionEntity);

    importPositionEntity.setAliasName(alias).setPartitionId(partitionId);
    return importPositionEntity;
  }

  @Override
  public Either<Throwable, Boolean> updateImportPositions(
      final List<ImportPositionEntity> positions,
      final List<ImportPositionEntity> postImportPositions) {
    if (positions.isEmpty() && postImportPositions.isEmpty()) {
      return Either.right(true);
    } else {
      final var bulkRequestBuilder = new BulkRequest.Builder();
      addPositions(bulkRequestBuilder, positions, ImportPositionUpdate::fromImportPositionEntity);
      addPositions(
          bulkRequestBuilder,
          postImportPositions,
          PostImportPositionUpdate::fromImportPositionEntity);

      try {
        withImportPositionTimer(
            () -> {
              richOpenSearchClient.batch().bulk(bulkRequestBuilder);
              return null;
            });

        return Either.right(true);
      } catch (final Throwable e) {
        LOGGER.error("Error occurred while persisting latest loaded position", e);
        return Either.left(e);
      }
    }
  }

  private void withImportPositionTimer(final Callable<Void> action) throws Exception {
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_POSITION_UPDATE).recordCallable(action);
  }

  private <R> void addPositions(
      final BulkRequest.Builder bulkRequestBuilder,
      final List<ImportPositionEntity> positions,
      final Function<ImportPositionEntity, R> entityProducer) {
    for (final ImportPositionEntity position : positions) {
      bulkRequestBuilder.operations(
          op ->
              op.update(
                  upd ->
                      upd.index(importPositionType.getFullQualifiedName())
                          .id(position.getId())
                          .upsert(entityProducer.apply(position))
                          .document(entityProducer.apply(position))));
    }
  }

  record ImportPositionUpdate(
      String id,
      String aliasName,
      String indexName,
      int partitionId,
      long position,
      Long postImporterPosition,
      long sequence) {
    public static ImportPositionUpdate fromImportPositionEntity(
        final ImportPositionEntity position) {
      return new ImportPositionUpdate(
          position.getId(),
          position.getAliasName(),
          position.getIndexName(),
          position.getPartitionId(),
          position.getPosition(),
          position.getPostImporterPosition(),
          position.getSequence());
    }
  }

  record PostImportPositionUpdate(Long postImporterPosition) {
    public static PostImportPositionUpdate fromImportPositionEntity(
        final ImportPositionEntity position) {
      return new PostImportPositionUpdate(position.getPostImporterPosition());
    }
  }
}
