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
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.schema.templates.VariableTemplate.FULL_VALUE;
import static io.camunda.operate.schema.templates.VariableTemplate.NAME;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.toSafeListOfStrings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchVariableReader implements VariableReader {

  @Autowired private OperateProperties operateProperties;
  @Autowired private VariableTemplate variableTemplate;

  @Autowired private OperationReader operationReader;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public List<VariableDto> getVariables(String processInstanceId, VariableRequestDto request) {
    List<VariableDto> response = queryVariables(processInstanceId, request);

    // query one additional instance
    if (request.getSearchAfterOrEqual() != null || request.getSearchBeforeOrEqual() != null) {
      adjustResponse(response, processInstanceId, request);
    }

    if (!response.isEmpty()
        && (request.getSearchAfter() != null || request.getSearchAfterOrEqual() != null)) {
      final VariableDto firstVar = response.get(0);
      firstVar.setIsFirst(checkVarIsFirst(processInstanceId, request, firstVar.getId()));
    }
    return response;
  }

  @Override
  public VariableDto getVariable(String id) {
    var searchRequest = searchRequestBuilder(variableTemplate).query(withTenantCheck(ids(id)));
    var hits = richOpenSearchClient.doc().search(searchRequest, VariableEntity.class).hits();
    if (hits.total().value() != 1) {
      throw new NotFoundException(String.format("Variable with id %s not found.", id));
    }
    return toVariableDto(hits.hits().get(0).source());
  }

  @Override
  public VariableDto getVariableByName(
      String processInstanceId, String scopeId, String variableName) {
    var searchRequest =
        searchRequestBuilder(variableTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            term(ProcessInstanceDependant.PROCESS_INSTANCE_KEY, processInstanceId),
                            term(VariableTemplate.SCOPE_KEY, scopeId),
                            term(VariableTemplate.NAME, variableName)))));
    var hits = richOpenSearchClient.doc().search(searchRequest, VariableEntity.class).hits();
    if (hits.total().value() > 0) {
      return toVariableDto(hits.hits().get(0).source());
    } else {
      return null;
    }
  }

  private void adjustResponse(
      final List<VariableDto> response,
      final String processInstanceId,
      final VariableRequestDto request) {
    String variableName = null;
    if (request.getSearchAfterOrEqual() != null) {
      variableName = (String) request.getSearchAfterOrEqual(objectMapper)[0];
    } else if (request.getSearchBeforeOrEqual() != null) {
      variableName = (String) request.getSearchBeforeOrEqual(objectMapper)[0];
    }

    VariableRequestDto newRequest =
        request
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null);

    final List<VariableDto> entities = queryVariables(processInstanceId, newRequest, variableName);
    if (!entities.isEmpty()) {
      final VariableDto entity = entities.get(0);
      entity.setIsFirst(false); // this was not the original query
      if (request.getSearchAfterOrEqual() != null) {
        // insert at the beginning of the list and remove the last element
        if (request.getPageSize() != null && response.size() == request.getPageSize()) {
          response.remove(response.size() - 1);
        }
        response.add(0, entity);
      } else if (request.getSearchBeforeOrEqual() != null) {
        // insert at the end of the list and remove the first element
        if (request.getPageSize() != null && response.size() == request.getPageSize()) {
          response.remove(0);
        }
        response.add(entity);
      }
    }
  }

  private boolean checkVarIsFirst(
      final String processInstanceId, final VariableRequestDto query, final String id) {
    final VariableRequestDto newQuery =
        query
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null)
            .setPageSize(1);
    final List<VariableDto> vars = queryVariables(processInstanceId, newQuery);
    if (!vars.isEmpty()) {
      return vars.get(0).getId().equals(id);
    } else {
      return false;
    }
  }

  private List<VariableDto> queryVariables(
      final String processInstanceId, VariableRequestDto variableRequest) {
    return queryVariables(processInstanceId, variableRequest, null);
  }

  private List<VariableDto> queryVariables(
      final String processInstanceId, VariableRequestDto request, String varName) {
    Long scopeKey = null;
    if (request.getScopeId() != null) {
      scopeKey = Long.valueOf(request.getScopeId());
    }
    var req =
        searchRequestBuilder(variableTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            term(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceId),
                            term(VariableTemplate.SCOPE_KEY, scopeKey),
                            (varName != null ? term(NAME, varName) : null)))))
            .source(sourceExclude(FULL_VALUE));
    applySorting(req, request);
    var response = richOpenSearchClient.doc().search(req, VariableEntity.class);
    List<VariableEntity> variableEntities =
        response.hits().hits().stream()
            .filter(hit -> hit.source() != null)
            .map(hit -> hit.source().setSortValues(hit.sort().toArray()))
            .toList();

    final Map<String, List<OperationEntity>> operations =
        operationReader.getUpdateOperationsPerVariableName(
            Long.valueOf(processInstanceId), scopeKey);
    final List<VariableDto> variables =
        VariableDto.createFrom(
            variableEntities,
            operations,
            operateProperties.getImporter().getVariableSizeThreshold(),
            objectMapper);

    if (!variables.isEmpty()) {
      if (request.getSearchBefore() != null || request.getSearchBeforeOrEqual() != null) {
        // in this case we were querying for size+1 results
        if (variables.size() <= request.getPageSize()) {
          // last task will be the first in the whole list
          variables.get(variables.size() - 1).setIsFirst(true);
        } else {
          // remove last task
          variables.remove(variables.size() - 1);
        }
        Collections.reverse(variables);
      } else if (request.getSearchAfter() == null && request.getSearchAfterOrEqual() == null) {
        variables.get(0).setIsFirst(true);
      }
    }
    return variables;
  }

  private void applySorting(
      final SearchRequest.Builder searchRequest, final VariableRequestDto request) {
    final boolean directSorting =
        request.getSearchAfter() != null
            || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    if (directSorting) { // this sorting is also the default one for 1st page
      searchRequest.sort(sortOptions(NAME, SortOrder.Asc));
      if (request.getSearchAfter() != null) {
        searchRequest.searchAfter(toSafeListOfStrings(request.getSearchAfter(objectMapper)));
      } else if (request.getSearchAfterOrEqual() != null) {
        searchRequest.searchAfter(toSafeListOfStrings(request.getSearchAfterOrEqual(objectMapper)));
      }
      searchRequest.size(request.getPageSize());
    } else { // searchBefore != null
      // reverse sorting
      searchRequest.sort(sortOptions(NAME, SortOrder.Desc));
      if (request.getSearchBefore() != null) {
        searchRequest.searchAfter(toSafeListOfStrings(request.getSearchBefore(objectMapper)));
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchRequest.searchAfter(
            toSafeListOfStrings(request.getSearchBeforeOrEqual(objectMapper)));
      }
      searchRequest.size(request.getPageSize() + 1);
    }
  }

  private VariableDto toVariableDto(VariableEntity variableEntity) {
    return VariableDto.createFrom(
        variableEntity,
        null,
        true,
        operateProperties.getImporter().getVariableSizeThreshold(),
        objectMapper);
  }
}
