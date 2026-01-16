/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchOperationStore implements OperationStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchOperationStore.class);

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private ElasticsearchClient esClient;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private BeanFactory beanFactory;

  @Override
  public Map<String, String> getIndexNameForAliasAndIds(
      final String alias, final Collection<String> ids) {

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(alias)
            .query(ElasticsearchUtil.idsQuery(ids.toArray(String[]::new)))
            .source(s -> s.fetch(Boolean.FALSE));

    try {
      final var resStream =
          ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, MAP_CLASS);

      return resStream
          .flatMap(res -> res.hits().hits().stream())
          .collect(Collectors.toMap(Hit::id, Hit::index));
    } catch (final ScrollException e) {
      throw new OperateRuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public List<OperationEntity> getOperationsFor(
      final Long zeebeCommandKey,
      final Long processInstanceKey,
      final Long incidentKey,
      final OperationType operationType) {
    if (processInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException(
          "Wrong call to search for operation. Not enough parameters.");
    }

    final var zeebeCommandKeyQ =
        zeebeCommandKey != null
            ? ElasticsearchUtil.termsQuery(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey)
            : null;
    final var processInstanceKeyQ =
        processInstanceKey != null
            ? ElasticsearchUtil.termsQuery(
                OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)
            : null;
    final var incidentKeyQ =
        incidentKey != null
            ? ElasticsearchUtil.termsQuery(OperationTemplate.INCIDENT_KEY, incidentKey)
            : null;
    final var operationTypeQ =
        operationType != null
            ? ElasticsearchUtil.termsQuery(OperationTemplate.TYPE, operationType.name())
            : null;

    final var query =
        ElasticsearchUtil.joinWithAnd(
            zeebeCommandKeyQ,
            processInstanceKeyQ,
            incidentKeyQ,
            operationTypeQ,
            ElasticsearchUtil.termsQuery(
                OperationTemplate.STATE,
                List.of(OperationState.SENT.name(), OperationState.LOCKED.name())));

    final var searchRequestBuilder =
        new SearchRequest.Builder().index(operationTemplate.getAlias()).size(1).query(query);

    try {
      return ElasticsearchUtil.scrollAllStream(
              esClient, searchRequestBuilder, OperationEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();
    } catch (final ScrollException e) {
      final String message =
          String.format("Exception occurred, while obtaining the operations: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String add(final BatchOperationEntity batchOperationEntity) throws PersistenceException {
    try {
      esClient.index(
          i ->
              i.index(batchOperationTemplate.getFullQualifiedName())
                  .id(batchOperationEntity.getId())
                  .document(batchOperationEntity));
    } catch (final IOException e) {
      LOGGER.error("Error persisting batch operation", e);
      throw new PersistenceException(
          String.format(
              "Error persisting batch operation of type [%s]", batchOperationEntity.getType()),
          e);
    }
    return batchOperationEntity.getId();
  }

  @Override
  public void update(final OperationEntity operation, final boolean refreshImmediately)
      throws PersistenceException {
    try {
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      esClient.update(
          u ->
              u.index(operationTemplate.getFullQualifiedName())
                  .id(operation.getId())
                  .doc(jsonMap)
                  .retryOnConflict(UPDATE_RETRY_COUNT)
                  .refresh(refreshImmediately ? Refresh.True : Refresh.False),
          Void.class);

    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to update operation [%s] for process instance id [%s]",
              operation.getId(), operation.getProcessInstanceKey()),
          e);
    }
  }

  @Override
  public void updateWithScript(
      final String index,
      final String id,
      final String script,
      final Map<String, Object> parameters) {
    try {
      esClient.update(
          u ->
              u.index(index)
                  .id(id)
                  .retryOnConflict(UPDATE_RETRY_COUNT)
                  .script(getScriptWithParameters(script, parameters)),
          Void.class);
    } catch (final Exception e) {
      final String message =
          String.format("Exception occurred, while executing update request: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }

  private Script getScriptWithParameters(final String script, final Map<String, Object> params) {
    final Map<String, JsonData> jsonDataParams =
        params.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue())));

    return new Script.Builder()
        .params(jsonDataParams)
        .source(script)
        .lang(ScriptLanguage.Painless)
        .build();
  }
}
