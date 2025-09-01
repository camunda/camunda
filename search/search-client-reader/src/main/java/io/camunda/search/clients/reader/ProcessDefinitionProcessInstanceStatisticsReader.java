package io.camunda.search.clients.reader;

import io.camunda.search.entities.ProcessDefinitionProcessInstanceStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionProcessInstanceStatisticsQuery;

public interface ProcessDefinitionProcessInstanceStatisticsReader
    extends SearchQueryStatisticsReader<
        ProcessDefinitionProcessInstanceStatisticsEntity,
        ProcessDefinitionProcessInstanceStatisticsQuery> {}
