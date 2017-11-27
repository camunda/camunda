package org.camunda.optimize.service.engine.importing.job;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.service.engine.importing.fetcher.instance.EngineEntityFetcher;
import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.engine.importing.diff.MissingEntitiesFinder;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.EventElasticsearchImportJob;

import java.util.List;

public class ActivityInstanceEngineImportJob extends
  EngineImportJob<HistoricActivityInstanceEngineDto, FlowNodeEventDto, DefinitionBasedImportPage> {

  private EventsWriter eventsWriter;

  public ActivityInstanceEngineImportJob(EventsWriter eventsWriter,
                                         DefinitionBasedImportPage importPage,
                                         ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                         MissingEntitiesFinder<HistoricActivityInstanceEngineDto> missingActivityFinder,
                                         EngineEntityFetcher<HistoricActivityInstanceEngineDto,
                                         DefinitionBasedImportPage> engineEntityFetcher,
                                         String engineAlias
  ) {
    super(importPage, elasticsearchImportJobExecutor, missingActivityFinder, engineEntityFetcher, engineAlias);
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
    return flowNodeEventDto;
  }

}
