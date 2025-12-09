package io.camunda.client.api.statistics.request;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.request.OffsetRequestPage;
import io.camunda.client.api.search.request.TypedSortableRequest;
import io.camunda.client.api.search.response.OffsetResponse;
import io.camunda.client.api.statistics.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceVersionStatistics;
import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import java.util.function.Consumer;

public interface ProcessDefinitionInstanceVersionStatisticsRequest
    extends StatisticsRequest<
            ProcessDefinitionInstanceVersionStatisticsFilter,
            ProcessDefinitionInstanceVersionStatisticsRequest>,
        TypedSortableRequest<
            ProcessDefinitionInstanceVersionStatisticsSort,
            ProcessDefinitionInstanceVersionStatisticsRequest>,
        FinalCommandStep<OffsetResponse<ProcessDefinitionInstanceVersionStatistics>> {

  ProcessDefinitionInstanceVersionStatisticsRequest page(OffsetRequestPage value);

  ProcessDefinitionInstanceVersionStatisticsRequest page(Consumer<OffsetRequestPage> fn);
}
