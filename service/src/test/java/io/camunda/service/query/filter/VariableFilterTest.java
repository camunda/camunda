package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.VariableServices;
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class VariableFilterTest {

  private VariableServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new VariableSearchQueryStub().registerWith(client);
    services = new VariableServices(client);
  }

  @Test
  public void shouldQueryOnlyByVariables() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (v) -> v.variable(new VariableValueFilter.Builder().name("foo").build()));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery((q) -> q.filter(variableFilter));

    // when
    services.search(searchQuery);

    // then

    // Assert: Transformation from VariableQuery to DataStoreSearchRequest

    // a) verify search request
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    // b) verify that the search request has been constructed properly
    // depending on the actual search query
    final SearchQueryOption queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (t) -> {
              assertThat(t.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("name");
                        assertThat(term.value().stringValue()).isEqualTo("foo");
                      });
            });
  }

  @Test
  public void shouldReturnVariables() {
    // given
    final var variableFilter =
        FilterBuilders.variable(
            (v) -> v.variable(new VariableValueFilter.Builder().name("foo").build()));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery((q) -> q.filter(variableFilter));

    // when
    final SearchQueryResult<VariableEntity> searchQueryResult = services.search(searchQuery);

    // then

    // Assert: Transformation from DataStoreSearchResponse to
    // SearchQueryResult<VariableEntity>

    // a) verify search query result
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);

    // b) assert items
    final VariableEntity item = searchQueryResult.items().get(0);
    assertThat(item.name()).isEqualTo("bar");
  }

  @Test
  public void shouldQueryByVariableScopeKey() {
    // given
    final var variableFilter = FilterBuilders.variable((v) -> v.scopeKeys(4503599627370497L));
    final var searchQuery =
        SearchQueryBuilders.variableSearchQuery((q) -> q.filter(variableFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("scopeKey");
              assertThat(t.value().longValue()).isEqualTo(4503599627370497L);
            });
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var variableFilter = new VariableFilter.Builder().build();

    // then
    assertThat(variableFilter.variableFilters()).isEmpty();
    assertThat(variableFilter.scopeKeys()).isEmpty();
    assertThat(variableFilter.processInstanceKeys()).isEmpty();
    assertThat(variableFilter.orConditions()).isFalse();
    assertThat(variableFilter.onlyRuntimeVariables()).isFalse();
  }

  @Test
  public void shouldSetFilterValues() {
    // given
    final var variableFilterBuilder = new VariableFilter.Builder();

    // when
    final var variableFilter =
        variableFilterBuilder
            .scopeKeys(1L)
            .processInstanceKeys(2L)
            .variable(new VariableValueFilter.Builder().name("foo").build())
            .orConditions(true)
            .onlyRuntimeVariables(true)
            .build();

    // then
    assertThat(variableFilter.scopeKeys()).hasSize(1).contains(1L);
    assertThat(variableFilter.processInstanceKeys()).hasSize(1).contains(2L);
    assertThat(variableFilter.variableFilters()).hasSize(1).extracting("name").contains("foo");
    assertThat(variableFilter.orConditions()).isTrue();
    assertThat(variableFilter.onlyRuntimeVariables()).isTrue();
  }
}
