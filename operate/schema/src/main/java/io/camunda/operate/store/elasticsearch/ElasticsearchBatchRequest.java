/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest.Builder;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.MissingRequiredPropertyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
@Scope(SCOPE_PROTOTYPE)
public class ElasticsearchBatchRequest implements BatchRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchBatchRequest.class);

  private final BulkRequest.Builder bulkRequestBuilder = new Builder();

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private OperateProperties operateProperties;

  @Autowired private ElasticsearchClient esClient;

  @Override
  public BatchRequest add(final String index, final ExporterEntity entity)
      throws PersistenceException {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final ExporterEntity entity) {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);
    bulkRequestBuilder.operations(
        op ->
            op.index(
                idx ->
                    idx.index(index)
                        .id(id)
                        .document(entity) // Serializer handles JSON automatically
                ));
    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final ExporterEntity entity, final String routing) {
    LOGGER.debug(
        "Add index request with routing {} for index {} and entity {} ", routing, index, entity);
    bulkRequestBuilder.operations(
        op ->
            op.index(idx -> idx.index(index).id(entity.getId()).document(entity).routing(routing)));
    return this;
  }

  @Override
  public BatchRequest upsert(
      final String index,
      final String id,
      final ExporterEntity entity,
      final Map<String, Object> updateFields) {
    LOGGER.debug(
        "Add upsert request for index {} id {} entity {} and update fields {}",
        index,
        id,
        entity,
        updateFields);
    bulkRequestBuilder.operations(
        op ->
            op.update(
                u ->
                    u.index(index)
                        .id(id)
                        .action(
                            a ->
                                a.doc(updateFields) // partial document to update
                                    .upsert(entity) // full document if missing
                            )));
    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields)
      throws PersistenceException {
    LOGGER.debug(
        "Add update request for index {} id {} and update fields {}", index, id, updateFields);
    bulkRequestBuilder.operations(
        op ->
            op.update(
                u ->
                    u.index(index)
                        .id(id)
                        .action(a -> a.doc(updateFields))
                        .retryOnConflict(UPDATE_RETRY_COUNT)));
    return this;
  }

  @Override
  public BatchRequest update(final String index, final String id, final ExporterEntity entity)
      throws PersistenceException {
    try {
      return update(
          index,
          id,
          objectMapper.readValue(objectMapper.writeValueAsString(entity), HashMap.class));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public BatchRequest updateWithScript(
      final String index,
      final String id,
      final String script,
      final Map<String, Object> parameters)
      throws PersistenceException {
    LOGGER.debug("Add update with script request for index {} id {} ", index, id);
    final Map<String, JsonData> paramsMap =
        parameters.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue())));
    // Create UpdateOperation

    bulkRequestBuilder.operations(
        op ->
            op.update(
                u ->
                    u.index(index)
                        .id(id)
                        .action(a -> a.script(s -> s.source(script).params(paramsMap)))
                        .retryOnConflict(UPDATE_RETRY_COUNT)));

    return this;
  }

  @Override
  public void execute() throws PersistenceException {
    try {
      ElasticsearchUtil.processBulkRequest(
          esClient,
          bulkRequestBuilder,
          operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    } catch (final MissingRequiredPropertyException ignored) {
      // empty bulk request
    }
  }

  @Override
  public void executeWithRefresh() {
    try {
      ElasticsearchUtil.processBulkRequest(
          esClient,
          bulkRequestBuilder,
          true,
          operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    } catch (final MissingRequiredPropertyException ignored) {
      // empty bulk request
    }
  }
}
