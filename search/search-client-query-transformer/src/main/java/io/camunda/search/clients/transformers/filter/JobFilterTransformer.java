package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.FLOW_NODE_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_STATE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_TYPE;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.JOB_WORKER;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.JobTemplate.PROCESS_INSTANCE_KEY;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.JobFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;

public class JobFilterTransformer extends IndexFilterTransformer<JobFilter> {

  public JobFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final JobFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(stringOperations(JOB_STATE, filter.stateOperations())).ifPresent(queries::addAll);
    ofNullable(stringOperations(JOB_TYPE, filter.typeOperations())).ifPresent(queries::addAll);
    ofNullable(stringOperations(JOB_WORKER, filter.workerOperations())).ifPresent(queries::addAll);
    ofNullable(longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(longOperations(PROCESS_DEFINITION_KEY, filter.processDefinitionKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(longOperations(FLOW_NODE_INSTANCE_ID, filter.elementInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations(FLOW_NODE_ID, filter.elementIdOperations()))
        .ifPresent(queries::addAll);

    return and(queries);
  }
}
