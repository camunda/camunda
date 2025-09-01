package io.camunda.search.clients.transformers.aggregation;

import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_NAME_LATEST_PROCESS_DEFINITION;
import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_NAME_PAGE;
import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_NAME_VERSION_COUNT;
import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_TERMS_SIZE;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.bucketSort;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.cardinality;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.filter;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.terms;
import static io.camunda.search.clients.aggregator.SearchAggregatorBuilders.topHits;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION;

import io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation;
import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator.Builder;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;

public class ProcessDefinitionProcessInstanceStatisticsAggregationTransformer
    implements AggregationTransformer<ProcessDefinitionProcessInstanceStatisticsAggregation> {

  @Override
  public List<SearchAggregator> apply(
      final Tuple<ProcessDefinitionProcessInstanceStatisticsAggregation, ServiceTransformers>
          value) {
    final var aggregation = value.getLeft();
    final Builder<ProcessInstanceForListViewEntity> topHits = topHits();

    final SearchTopHitsAggregator<ProcessInstanceForListViewEntity> latestProcessDefinitionAgg =
        topHits
            .name(AGGREGATION_NAME_LATEST_PROCESS_DEFINITION)
            .field(PROCESS_VERSION)
            .size(1)
            .documentClass(ProcessInstanceForListViewEntity.class)
            .build();

    final var versionCountAgg =
        cardinality().name(AGGREGATION_NAME_VERSION_COUNT).field(PROCESS_VERSION).build();

    final var totalWithIncidentsAgg =
        filter()
            .name(
                ProcessDefinitionProcessInstanceStatisticsAggregation
                    .AGGREGATION_NAME_TOTAL_WITH_INCIDENT)
            .query(term(INCIDENT, true))
            .build();

    final var totalWithoutIncidentsAgg =
        filter()
            .name(
                ProcessDefinitionProcessInstanceStatisticsAggregation
                    .AGGREGATION_NAME_TOTAL_WITHOUT_INCIDENT)
            .query(term(INCIDENT, false))
            .build();

    final var byProcessDefinitionIdAggBuilder =
        terms()
            .name(
                ProcessDefinitionProcessInstanceStatisticsAggregation
                    .AGGREGATION_NAME_BY_PROCESS_ID)
            .field(BPMN_PROCESS_ID)
            .size(AGGREGATION_TERMS_SIZE)
            .sorting(
                List.of(
                    new FieldSorting(AGGREGATION_FIELD_KEY, io.camunda.search.sort.SortOrder.ASC)));

    final var bucketSort =
        bucketSort()
            .name(AGGREGATION_NAME_PAGE)
            .sorting(getCountSuffixSortings(aggregation))
            .from(aggregation.page() != null ? aggregation.page().from() : null)
            .size(aggregation.page() != null ? aggregation.page().size() : null)
            .build();

    final var byProcessDefinitionIdAgg =
        byProcessDefinitionIdAggBuilder
            .aggregations(
                latestProcessDefinitionAgg,
                versionCountAgg,
                totalWithIncidentsAgg,
                totalWithoutIncidentsAgg,
                bucketSort)
            .build();

    return List.of(byProcessDefinitionIdAgg);
  }

  private static List<FieldSorting> getCountSuffixSortings(
      final ProcessDefinitionProcessInstanceStatisticsAggregation aggregation) {
    return aggregation.sort().getFieldSortings().stream()
        .map(ordering -> new FieldSorting(ordering.field() + "._count", ordering.order()))
        .toList();
  }
}
