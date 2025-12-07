package io.camunda.client.api.statistics.request;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.request.OffsetRequestPage;
import io.camunda.client.api.search.request.TypedSortableRequest;
import io.camunda.client.api.search.response.OffsetResponse;
import io.camunda.client.api.statistics.response.ProcessDefinitionInstanceStatistics;
import io.camunda.client.api.statistics.sort.ProcessDefinitionInstanceStatisticsSort;
import java.util.function.Consumer;

public interface ProcessDefinitionInstanceStatisticsRequest
    extends TypedSortableRequest<
            ProcessDefinitionInstanceStatisticsSort, ProcessDefinitionInstanceStatisticsRequest>,
        FinalCommandStep<OffsetResponse<ProcessDefinitionInstanceStatistics>> {

  ProcessDefinitionInstanceStatisticsRequest page(OffsetRequestPage value);

  ProcessDefinitionInstanceStatisticsRequest page(Consumer<OffsetRequestPage> fn);
}
