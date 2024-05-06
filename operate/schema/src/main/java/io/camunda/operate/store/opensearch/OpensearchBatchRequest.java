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
  public BatchRequest add(final String index, final OperateEntity entity)
      throws PersistenceException {
    return addWithId(index, entity.getId(), entity);
  }

  @Override
  public BatchRequest addWithId(final String index, final String id, final OperateEntity entity)
      throws PersistenceException {
    LOGGER.debug("Add index request for index {} id {} and entity {} ", index, id, entity);

    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op -> op.index(idx -> idx.index(index).id(id).document(entity))));

    return this;
  }

  @Override
  public BatchRequest addWithRouting(
      final String index, final OperateEntity entity, final String routing)
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
      final String index,
      final String id,
      final OperateEntity entity,
      final Map<String, Object> updateFields)
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
      final String index,
      final String id,
      final OperateEntity entity,
      final Map<String, Object> updateFields,
      final String routing)
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
  public BatchRequest upsertWithScript(
      final String index,
      final String id,
      final OperateEntity entity,
      final String script,
      final Map<String, Object> parameters)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with for index {} id {} entity {} and script {} with parameters {} ",
        index,
        id,
        entity,
        script,
        parameters);
    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        upd ->
                            upd.index(index)
                                .id(id)
                                .upsert(entity)
                                .script(script(script, parameters))
                                .retryOnConflict(UPDATE_RETRY_COUNT))),
        String.format(
            String.format(
                "Error preparing the query to upsert [%s] of entity type [%s] with script and routing",
                entity.getClass().getName(), entity),
            index,
            id));
    return this;
  }

  @Override
  public BatchRequest upsertWithScriptAndRouting(
      final String index,
      final String id,
      final OperateEntity entity,
      final String script,
      final Map<String, Object> parameters,
      final String routing)
      throws PersistenceException {
    LOGGER.debug(
        "Add upsert request with routing {} for index {} id {} entity {} and script {} with parameters {} ",
        routing,
        index,
        id,
        entity,
        script,
        parameters);
    withPersistenceException(
        () ->
            bulkRequestBuilder.operations(
                op ->
                    op.update(
                        upd ->
                            upd.index(index)
                                .id(id)
                                .upsert(entity)
                                .script(script(script, parameters))
                                .routing(routing)
                                .retryOnConflict(UPDATE_RETRY_COUNT))),
        String.format(
            String.format(
                "Error preparing the query to upsert [%s] of entity type [%s] with script and routing",
                entity.getClass().getName(), entity),
            index,
            id));
    return this;
  }

  @Override
  public BatchRequest update(
      final String index, final String id, final Map<String, Object> updateFields)
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
  public BatchRequest update(final String index, final String id, final OperateEntity entity)
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
      final String index,
      final String id,
      final String script,
      final Map<String, Object> parameters)
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

  private void execute(final Boolean shouldRefresh) throws PersistenceException {
    if (shouldRefresh) {
      bulkRequestBuilder.refresh(Refresh.True);
    }

    final BulkRequest bulkRequest;
    try {
      bulkRequest = bulkRequestBuilder.build();
    } catch (final MissingRequiredPropertyException e) {
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
