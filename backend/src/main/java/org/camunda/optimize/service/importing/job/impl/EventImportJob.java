package org.camunda.optimize.service.importing.job.impl;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.EventDto;
import org.camunda.optimize.service.es.writer.EventsWriter;
import org.camunda.optimize.service.importing.EngineEntityFetcher;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  protected void getAbsentAggregateInformation() {
    List<HistoricProcessInstanceDto> processInstances = getHistoricProcessInstancesFromNewEntities();
    Map<String, HistoricProcessInstanceDto> processInstanceMap = createHistoricProcessInstanceMap(processInstances);
    addMissingAggregateInformationToNewEntities(processInstanceMap);
  }

  private List<HistoricProcessInstanceDto> getHistoricProcessInstancesFromNewEntities() {
    List<String> processInstanceIds =
      newOptimizeEntities.stream().map(EventDto::getProcessInstanceId).collect(Collectors.toList());
    String[] processInstanceIdArray = processInstanceIds.toArray(new String[processInstanceIds.size()]);
    return engineEntityFetcher.fetchHistoricProcessInstances(processInstanceIdArray);
  }

  private Map<String, HistoricProcessInstanceDto> createHistoricProcessInstanceMap(List<HistoricProcessInstanceDto> processInstances) {
    Map<String, HistoricProcessInstanceDto> processInstanceMap = new HashMap<>();
    for (HistoricProcessInstanceDto processInstanceDto : processInstances) {
      processInstanceMap.put(processInstanceDto.getId(), processInstanceDto);
    }
    return processInstanceMap;
  }

  private void addMissingAggregateInformationToNewEntities(Map<String, HistoricProcessInstanceDto> processInstanceMap) {
    for (EventDto optimizeEntity : newOptimizeEntities) {
      if(processInstanceMap.containsKey(optimizeEntity.getProcessInstanceId())) {
        HistoricProcessInstanceDto procInst = processInstanceMap.get(optimizeEntity.getProcessInstanceId());
        optimizeEntity.setProcessInstanceStartDate(procInst.getStartTime());
        optimizeEntity.setProcessInstanceEndDate(procInst.getEndTime());
      } else {
        logger.warn("Could not add missing aggregate data to event during import!");
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
