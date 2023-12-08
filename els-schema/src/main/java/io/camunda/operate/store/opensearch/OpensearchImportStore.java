/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.store.ImportStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.Either;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchImportStore implements ImportStore {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchImportStore.class);
  @Autowired
  private ImportPositionIndex importPositionType;
  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Metrics metrics;

  @Autowired
  private OperateProperties operateProperties;
  @Override
  public ImportPositionEntity getImportPositionByAliasAndPartitionId(String alias, int partitionId) throws IOException {
    var searchRequestBuilder = searchRequestBuilder(importPositionType.getAlias())
      .size(10)
      .query(
        and(
          term(ImportPositionIndex.ALIAS_NAME, alias),
          term(ImportPositionIndex.PARTITION_ID, partitionId)
        )
      );

      var response = richOpenSearchClient.doc().search(searchRequestBuilder, ImportPositionEntity.class);

      ImportPositionEntity importPositionEntity = new ImportPositionEntity();
      if(!response.hits().hits().isEmpty()) {
        importPositionEntity = response.hits().hits().get(0).source();
      }
      logger.debug("Latest loaded position for alias [{}] and partitionId [{}]: {}", alias, partitionId, importPositionEntity);

      importPositionEntity.setAliasName(alias).setPartitionId(partitionId);
      return importPositionEntity;
  }

  @Override
  public Either<Throwable, Boolean> updateImportPositions(List<ImportPositionEntity> positions, List<ImportPositionEntity> postImportPositions) {
    if(positions.isEmpty() && postImportPositions.isEmpty()) {
      return Either.right(true);
    } else {
      final var bulkRequestBuilder = new BulkRequest.Builder();
      addPositions(bulkRequestBuilder, positions, ImportPositionUpdate::fromImportPositionEntity);
      addPositions(bulkRequestBuilder, postImportPositions, PostImportPositionUpdate::fromImportPositionEntity);

      try {
        withImportPositionTimer(() -> {
          richOpenSearchClient.batch().bulk(bulkRequestBuilder);
          return null;
        });

        return Either.right(true);
      } catch (final Throwable e) {
        logger.error("Error occurred while persisting latest loaded position", e);
        return Either.left(e);
      }
    }
  }

  private void withImportPositionTimer(final Callable<Void> action) throws Exception {
    metrics.getTimer(Metrics.TIMER_NAME_IMPORT_POSITION_UPDATE).recordCallable(action);
  }

  private <R> void addPositions(final BulkRequest.Builder bulkRequestBuilder, final List<ImportPositionEntity> positions, Function<ImportPositionEntity, R> entityProducer) {
    for(ImportPositionEntity position: positions) {
      bulkRequestBuilder.operations(op ->
          op.update(upd -> upd
              .index(importPositionType.getFullQualifiedName())
              .id(position.getId())
              .upsert(entityProducer.apply(position))
              .document(entityProducer.apply(position))
          )
      );
    }
  }

  record ImportPositionUpdate(String id,String aliasName,String indexName,int partitionId,long position,Long postImporterPosition,long sequence) {
    public static ImportPositionUpdate fromImportPositionEntity(final ImportPositionEntity position) {
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
    public static PostImportPositionUpdate fromImportPositionEntity(final ImportPositionEntity position) {
      return new PostImportPositionUpdate(position.getPostImporterPosition());
    }
  }

}
