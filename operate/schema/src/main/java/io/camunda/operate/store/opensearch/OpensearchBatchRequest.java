/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchRetryOperation.UPDATE_RETRY_COUNT;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.script;
import static io.camunda.operate.util.ExceptionHelper.withPersistenceException;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.Map;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.util.MissingRequiredPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class OpensearchBatchRequest implements BatchRequest {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchBatchRequest.class);
  private final BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public BatchRequest add(String index, OperateEntity entity) throws PersistenceException {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(String index, String id, OperateEntity entity)
      throws PersistenceException {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);

    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op -> op.index(idx -> idx.index(index).id(id).document(entity))));

    return this;
  }

  @Override
  public BatchRequest addWithRouting(String index, OperateEntity entity, String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);

    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.index(
                        idx ->
                            idx.index(index).id(entity.getId()).document(entity).routing(routing))),
        String.format(
            "Error preparing the query to index [%s] of entity type [%s] with routing",
            entity.getClass().getName(), entity));

    return this;
  }

  @Override
  public BatchRequest upsert(
      String index, String id, OperateEntity entity, Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request for index {} id {} entity {} and update fields {}",
        index,
        id,
        entity,
        updateFields);

    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        upd -> upd.index(index).id(id).upsert(entity).document(updateFields))),
        String.format(
            "Error preparing the query to upsert [%s] of entity type [%s]",
            entity.getClass().getName(), entity));

    return this;
  }

  @Override
  public BatchRequest upsertWithRouting(
      String index,
      String id,
      OperateEntity entity,
      Map<String, Object> updateFields,
      String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and update fields {}",
        routing,
        index,
        id,
        entity,
        updateFields);

    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        upd ->
                            upd.index(index)
                                .id(id)
                                .upsert(entity)
                                .document(updateFields)
                                .routing(routing))),
        String.format(
            "Error preparing the query to upsert [%s] of entity type [%s] with routing",
            entity.getClass().getName(), entity));

    return this;
  }

  @Override
  public BatchRequest update(String index, String id, Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);

    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        upd ->
                            upd.index(index)
                                .id(id)
                                .document(updateFields)
                                .retryOnConflict(UPDATE_RETRY_COUNT))),
        String.format(
            "Error preparing the query to update index [%s] document with id [%s]", index, id));

    return this;
  }

  @Override
  public BatchRequest update(String index, String id, OperateEntity entity)
      throws PersistenceException {
    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        upd ->
                            upd.index(index)
                                .id(id)
                                .document(entity)
                                .retryOnConflict(UPDATE_RETRY_COUNT))),
        String.format(
            "Error preparing the query to update index [%s] document with id [%s]", index, id));

    return this;
  }

  @Override
  public BatchRequest updateWithScript(
      String index, String id, String script, Map<String, Object> parameters)
      throws PersistenceException {
    LOGGER.debug("Add update with script request for index {} id {} ", index, id);

    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        upd ->
                            upd.index(index)
                                .id(id)
                                .script(script(script, parameters))
                                .retryOnConflict(UPDATE_RETRY_COUNT))),
        String.format(
            "Error preparing the query to update index [%s] document with id [%s]", index, id));

    return this;
  }

  @Override
  public void execute() throws PersistenceException {
    execute(false);
  }

  @Override
  public void executeWithRefresh() throws PersistenceException {
    execute(true);
  }

  private void execute(Boolean shouldRefresh) throws PersistenceException {
    if (shouldRefresh) {
      bulkRequestBuilder.refresh(Refresh.True);
    }

    final BulkRequest bulkRequest;
    try {
      bulkRequest = bulkRequestBuilder.build();
    } catch (MissingRequiredPropertyException e) {
      if ("Missing required property 'BulkRequest.operations'".equals(e.getMessage())) {
        return;
      } else {
        throw e;
      }
    }

    LOGGER.debug("Execute batchRequest with {} requests", bulkRequest.operations().size());

    withPersistenceException(
        () -> {
          richOpenSearchClient.batch().bulk(bulkRequest);
          return null;
        });
  }
}
