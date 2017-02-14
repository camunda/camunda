package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessDefinitionImportService implements ImportService {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionFinder processDefinitionFinder;

  @Override
  public void executeImport() {

    List<ProcessDefinitionEngineDto> engineEntries = processDefinitionFinder.retrieveMissingEntities();

    List<ProcessDefinitionOptimizeDto> optimizeEntries = mapToOptimizeDto(engineEntries);
    logger.warn("Number of importing process definitions: " + optimizeEntries.size());
    try {
      procDefWriter.importProcessDefinitions(optimizeEntries);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }

  private List<ProcessDefinitionOptimizeDto> mapToOptimizeDto(List<ProcessDefinitionEngineDto> entries) {
    List<ProcessDefinitionOptimizeDto> result = new ArrayList<>(entries.size());
    for (ProcessDefinitionEngineDto entry : entries) {
      mapDefaults(entry);

      result.add(mapDefaults(entry));
    }

    return result;
  }

  private ProcessDefinitionOptimizeDto mapDefaults(ProcessDefinitionEngineDto dto) {
    ProcessDefinitionOptimizeDto optimizeDto = new ProcessDefinitionOptimizeDto();
    optimizeDto.setName(dto.getName());
    optimizeDto.setKey(dto.getKey());
    optimizeDto.setId(dto.getId());
    return optimizeDto;
  }
}
