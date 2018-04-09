package org.camunda.optimize.service.engine.importing.service;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.EventElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.EventsWriter;

import java.util.List;

public class ActivityInstanceImportService extends
  ImportService<HistoricActivityInstanceEngineDto, FlowNodeEventDto> {

  private EventsWriter eventsWriter;

  public ActivityInstanceImportService(EventsWriter eventsWriter,
                                       ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                       MissingEntitiesFinder<HistoricActivityInstanceEngineDto> missingActivityFinder,
                                       EngineContext engineContext
  ) {
    super(elasticsearchImportJobExecutor, missingActivityFinder, engineContext);
    this.eventsWriter = eventsWriter;
  }

  @Override
  protected ElasticsearchImportJob<FlowNodeEventDto> createElasticsearchImportJob(List<FlowNodeEventDto> events) {
    EventElasticsearchImportJob eventImportJob = new EventElasticsearchImportJob(eventsWriter);
    eventImportJob.setEntitiesToImport(events);
    return eventImportJob;
  }

  @Override
  protected FlowNodeEventDto mapEngineEntityToOptimizeEntity(HistoricActivityInstanceEngineDto engineEntity) {
    FlowNodeEventDto flowNodeEventDto = new FlowNodeEventDto();
    flowNodeEventDto.setId(engineEntity.getId());
    flowNodeEventDto.setActivityId(engineEntity.getActivityId());
    flowNodeEventDto.setActivityInstanceId(engineEntity.getParentActivityInstanceId());
    flowNodeEventDto.setTimestamp(engineEntity.getStartTime());
    flowNodeEventDto.setProcessDefinitionKey(engineEntity.getProcessDefinitionKey());
    flowNodeEventDto.setProcessDefinitionId(engineEntity.getProcessDefinitionId());
    flowNodeEventDto.setProcessInstanceId(engineEntity.getProcessInstanceId());
    flowNodeEventDto.setStartDate(engineEntity.getStartTime());
    flowNodeEventDto.setEndDate(engineEntity.getEndTime());
    flowNodeEventDto.setActivityType(engineEntity.getActivityType());
    flowNodeEventDto.setDurationInMs(engineEntity.getDurationInMillis());
    flowNodeEventDto.setEngineAlias(engineContext.getEngineAlias());
    return flowNodeEventDto;
  }

}
