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
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.script;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.stringTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ExceptionHelper.withPersistenceException;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperationStore implements OperationStore {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchOperationStore.class);
  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private BeanFactory beanFactory;

  @Override
  public Map<String, String> getIndexNameForAliasAndIds(String alias, Collection<String> ids) {
    return richOpenSearchClient.doc().getIndexNames(alias, ids);
  }

  @Override
  public List<OperationEntity> getOperationsFor(
      Long zeebeCommandKey,
      Long processInstanceKey,
      Long incidentKey,
      OperationType operationType) {
    if (processInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException(
          "Wrong call to search for operation. Not enough parameters.");
    }

    final Query zeebeCommandKeyQ =
        zeebeCommandKey != null ? term(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey) : null;
    final Query processInstanceKeyQ =
        processInstanceKey != null
            ? term(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)
            : null;
    final Query incidentKeyQ =
        incidentKey != null ? term(OperationTemplate.INCIDENT_KEY, incidentKey) : null;
    final Query operationTypeQ =
        operationType != null ? term(OperationTemplate.TYPE, operationType.name()) : null;
    final Query query =
        and(
            zeebeCommandKeyQ,
            processInstanceKeyQ,
            incidentKeyQ,
            operationTypeQ,
            stringTerms(
                OperationTemplate.STATE,
                List.of(OperationState.SENT.name(), OperationState.LOCKED.name())));
    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate.getAlias()).query(query).size(1);

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
  }

  @Override
  public String add(BatchOperationEntity batchOperationEntity) throws PersistenceException {
    var indexRequestBuilder =
        RequestDSL.<BatchOperationEntity>indexRequestBuilder(
                batchOperationTemplate.getFullQualifiedName())
            .id(batchOperationEntity.getId())
            .document(batchOperationEntity);

    withPersistenceException(() -> richOpenSearchClient.doc().index(indexRequestBuilder));

    return batchOperationEntity.getId();
  }

  @Override
  public void update(OperationEntity operation, boolean refreshImmediately)
      throws PersistenceException {
    final Function<Exception, String> errorMessageSupplier =
        e ->
            String.format(
                "Error preparing the query to update operation [%s] for process instance id [%s]",
                operation.getId(), operation.getProcessInstanceKey());
    var updateRequestBuilder =
        RequestDSL.<OperationEntity, Void>updateRequestBuilder(
                operationTemplate.getFullQualifiedName())
            .id(operation.getId())
            .doc(operation)
            .retryOnConflict(UPDATE_RETRY_COUNT);

    if (refreshImmediately) {
      updateRequestBuilder.refresh(Refresh.True);
    }

    withPersistenceException(
        () -> richOpenSearchClient.doc().update(updateRequestBuilder, errorMessageSupplier));
  }

  @Override
  public void updateWithScript(
      String index, String id, String script, Map<String, Object> parameters) {
    final Function<Exception, String> errorMessageSupplier =
        e ->
            String.format(
                "Exception occurred, while executing update request with script for operation [%s]",
                id);
    var updateRequestBuilder =
        RequestDSL.<Void, Void>updateRequestBuilder(index)
            .id(id)
            .script(script(script, parameters))
            .retryOnConflict(UPDATE_RETRY_COUNT);

    richOpenSearchClient.doc().update(updateRequestBuilder, errorMessageSupplier);
  }

  @Override
  public BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }
}
