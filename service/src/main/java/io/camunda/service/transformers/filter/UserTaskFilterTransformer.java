package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.wildcardQuery;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.query.QueryFieldFilterTransformers;
import io.camunda.service.query.filter.FilterOperator;
import io.camunda.service.search.filter.ComparableValueFilter;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.query.filter.FieldFilter;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.service.transformers.filter.ComparableValueFilterTransformer.ComparableFieldFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class UserTaskFilterTransformer implements FilterTransformer<UserTaskFilter> {
  private final ServiceTransformers transformers;

  public UserTaskFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final UserTaskFilter filter) {
    final var userTaskKeysQuery = getUserTaskKeysQuery(filter.keys());

    final var processInstanceKeysQuery = getProcessInstanceKeysQuery(filter.processInstanceKeys());
    final var processDefinitionKeyQuery =
        getProcessDefinitionKeyQuery(filter.processDefinitionKeys());
    final var bpmnProcessDefinitionIdQuery = getBpmnProcessIdQuery(filter.bpmnProcessIds());
    final var elementIdQuery = getElementIdQuery(filter.elementIds());

    final var candidateUsersQuery = getCandidateUsersQuery(filter.candidateUsers());
    final var candidateGroupsQuery = getCandidateGroupsQuery(filter.candidateGroups());

    final var assigneesQuery = getAssigneesQuery(filter.assignees());
    final var stateQuery = getStateQuery(filter.states());  // Updated for $eq and $like
    final var tenantQuery = getTenantQuery(filter.tenantIds());
    final var priorityQuery = getComparableFilter(filter.priority(), "priority");

    // Temporary internal condition - in order to bring only Zeebe User Tasks from Tasklist Indices
    final var userTaksImplementationQuery = getUserTasksImplementationOnly();

    return and(
        userTaskKeysQuery,
        bpmnProcessDefinitionIdQuery,
        candidateUsersQuery,
        candidateGroupsQuery,
        assigneesQuery,
        stateQuery,
        processInstanceKeysQuery,
        processDefinitionKeyQuery,
        tenantQuery,
        userTaksImplementationQuery,
        elementIdQuery,
        priorityQuery);
  }

  @Override
  public List<String> toIndices(final UserTaskFilter filter) {
    if (filter != null && filter.states() != null && filter.states().getValue() != null && !filter.states().getValue().isEmpty()) {
      if (Objects.equals(filter.states().getValue().get(0), "CREATED") && filter.states().getValue().size() == 1) {
        return Arrays.asList("tasklist-task-8.5.0_"); // Not necessary to visit alias in this case
      }
    }
    return Arrays.asList("tasklist-task-8.5.0_alias");
  }

  private SearchQuery getComparableFilter(final ComparableValueFilter filter, final String field) {
    if (filter != null) {
      final var transformer = getComparableFilterTransformer();
      return transformer.apply(new ComparableFieldFilter(field, filter));
    }
    return null;
  }

  private SearchQuery getProcessInstanceKeysQuery(final List<Long> processInstanceKeys) {
    return longTerms("processInstanceId", processInstanceKeys);
  }

  private SearchQuery getProcessDefinitionKeyQuery(final List<Long> processDefinitionIds) {
    return longTerms("processDefinitionId", processDefinitionIds);
  }

  private SearchQuery getUserTaskKeysQuery(final FieldFilter<List<Long>> keys) {
    if (keys != null && keys.getValue() != null && !keys.getValue().isEmpty()) {
      final FilterOperator operator = keys.getOperator();
      return QueryFieldFilterTransformers.buildLongQuery("key", keys.getValue(), operator);
    }
    return null;
  }

  private SearchQuery getUserTasksImplementationOnly() {
    return term("implementation", "ZEEBE_USER_TASK");
  }

  private SearchQuery getCandidateUsersQuery(final List<String> candidateUsers) {
    return stringTerms("candidateUsers", candidateUsers);
  }

  private SearchQuery getCandidateGroupsQuery(final List<String> candidateGroups) {
    return stringTerms("candidateGroups", candidateGroups);
  }

  private SearchQuery getAssigneesQuery(final List<String> assignee) {
    return stringTerms("assignee", assignee);
  }

  // Updated to support both $eq and $like operators
  private SearchQuery getStateQuery(final FieldFilter<List<String>> states) {
    if (states != null && states.getValue() != null && !states.getValue().isEmpty()) {
      final FilterOperator operator = states.getOperator();
      return QueryFieldFilterTransformers.buildStringQuery("state", states.getValue(), operator);
    }
    return null;
  }

  private SearchQuery getTenantQuery(final List<String> tenants) {
    return stringTerms("tenantId", tenants);
  }

  private SearchQuery getBpmnProcessIdQuery(final List<String> bpmnProcessIds) {
    return stringTerms("bpmnProcessId", bpmnProcessIds);
  }

  private SearchQuery getElementIdQuery(final List<String> elementIds) {
    return stringTerms("flowNodeBpmnId", elementIds);
  }

  private FilterTransformer<ComparableFieldFilter> getComparableFilterTransformer() {
    return transformers.getFilterTransformer(ComparableValueFilter.class);
  }
}
