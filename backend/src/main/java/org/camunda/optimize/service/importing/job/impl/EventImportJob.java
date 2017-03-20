package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.EngineEntityFetcher;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EventImportJob extends ImportJob<EventDto>{

  private EventsWriter eventsWriter;
  private EngineEntityFetcher engineEntityFetcher;
  private Logger logger = LoggerFactory.getLogger(EventImportJob.class);

  public EventImportJob(EngineEntityFetcher engineEntityFetcher, EventsWriter eventsWriter) {
    this.engineEntityFetcher = engineEntityFetcher;
    this.eventsWriter = eventsWriter;
  }
  @Override
  protected void getAbsentAggregateInformation() throws OptimizeException {
    List<HistoricProcessInstanceDto> processInstances = getHistoricProcessInstancesFromNewEntities();
    Map<String, HistoricProcessInstanceDto> processInstanceMap = createHistoricProcessInstanceMap(processInstances);
    addMissingAggregateInformationToNewEntities(processInstanceMap);
  }

  private List<HistoricProcessInstanceDto> getHistoricProcessInstancesFromNewEntities() {
    Set<String> processInstanceIds =
      newOptimizeEntities.stream().map(EventDto::getProcessInstanceId).collect(Collectors.toSet());
    return engineEntityFetcher.fetchHistoricProcessInstances(processInstanceIds);
  }

  private Map<String, HistoricProcessInstanceDto> createHistoricProcessInstanceMap(List<HistoricProcessInstanceDto> processInstances) {
    Map<String, HistoricProcessInstanceDto> processInstanceMap = new HashMap<>();
    for (HistoricProcessInstanceDto processInstanceDto : processInstances) {
      processInstanceMap.put(processInstanceDto.getId(), processInstanceDto);
    }
    return processInstanceMap;
  }

  private void addMissingAggregateInformationToNewEntities(Map<String, HistoricProcessInstanceDto> processInstanceMap) throws OptimizeException {
    for (EventDto optimizeEntity : newOptimizeEntities) {
      if(processInstanceMap.containsKey(optimizeEntity.getProcessInstanceId())) {
        HistoricProcessInstanceDto procInst = processInstanceMap.get(optimizeEntity.getProcessInstanceId());
        optimizeEntity.setProcessInstanceStartDate(procInst.getStartTime());
        optimizeEntity.setProcessInstanceEndDate(procInst.getEndTime());
      } else {
        throw new OptimizeException("Data for aggregation is not fetched properly");
      }
    }
  }

  @Override
  protected void executeImport() {
    try {
      eventsWriter.importEvents(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing events to elasticsearch", e);
    }
  }
}
