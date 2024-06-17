package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.range;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.transformers.ServiceTransformers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VariableFilterTransformer implements FilterTransformer<VariableFilter> {
  private final ServiceTransformers transformers;

  public VariableFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final VariableFilter filter) {
    final var variablesQuery = getVariablesQuery(filter.variableFilters(), filter.orConditions());
    final var scopeKeyQuery = getScopeKeyQuery(filter.scopeKeys());
    final var processInstanceKeyQuery = getProcessInstanceKeyQuery(filter.processInstanceKeys());

    return and(variablesQuery, scopeKeyQuery, processInstanceKeyQuery);
  }

  @Override
  public List<String> toIndices(final VariableFilter filter) {
    return Arrays.asList("operate-variable-8.3.0_alias");
  }

  private SearchQuery getVariablesQuery(
      final List<VariableValueFilter> variableFilters, final boolean orConditions) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var queries =
          variableFilters.stream()
              .map(this::transformVariableValueFilter)
              .collect(Collectors.toList());
      return orConditions ? or(queries) : and(queries);
    }
    return null;
  }

  private SearchQuery transformVariableValueFilter(final VariableValueFilter value) {
    final var name = value.name();
    final var eq = value.eq();
    final var neq = value.neq();
    final var gt = value.gt();
    final var gte = value.gte();
    final var lt = value.lt();
    final var lte = value.lte();

    final var variableNameQuery = term("name", name);
    final SearchQuery variableValueQuery;

    if (eq != null) {
      variableValueQuery = of(eq);
    } else if (neq != null) {
      variableValueQuery = not(of(neq));
    } else {
      final var builder = range().field("value");

      if (gt != null) {
        builder.gt(gt);
      }

      if (gte != null) {
        builder.gte(gte);
      }

      if (lt != null) {
        builder.lt(lt);
      }

      if (lte != null) {
        builder.lte(lte);
      }

      variableValueQuery = builder.build().toSearchQuery();
    }

    return and(variableNameQuery, variableValueQuery);
  }

  private SearchQuery of(final Object value) {
    final var typedValue = TypedValue.toTypedValue(value);
    return SearchQueryBuilders.term().field("value").value(typedValue).build().toSearchQuery();
  }

  private SearchQuery getScopeKeyQuery(final List<Long> scopeKey) {
    return longTerms("scopeKey", scopeKey);
  }

  private SearchQuery getProcessInstanceKeyQuery(final List<Long> processInstanceKey) {
    return longTerms("processInstanceKey", processInstanceKey);
  }
}
