package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionXmlFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessDefinitionXmlImportService implements ImportService {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionXmlFinder xmlFinder;

  @Override
  public void executeImport() {
    List<ProcessDefinitionXmlEngineDto> engineEntries = xmlFinder.retrieveMissingEntities();

    List<ProcessDefinitionXmlOptimizeDto> optimizeDtos = mapToOptimizeDto(engineEntries);

    try {
      procDefWriter.importProcessDefinitionXmls(optimizeDtos);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }

  }

  private List<ProcessDefinitionXmlOptimizeDto> mapToOptimizeDto(List<ProcessDefinitionXmlEngineDto> entries) {
    List<ProcessDefinitionXmlOptimizeDto> result = new ArrayList<>(entries.size());
    for (ProcessDefinitionXmlEngineDto entry : entries) {
      mapDefaults(entry);

      result.add(mapDefaults(entry));
    }

    return result;
  }

  private ProcessDefinitionXmlOptimizeDto mapDefaults(ProcessDefinitionXmlEngineDto dto) {
    ProcessDefinitionXmlOptimizeDto optimizeDto = new ProcessDefinitionXmlOptimizeDto();
    optimizeDto.setBpmn20Xml(dto.getBpmn20Xml());
    optimizeDto.setId(dto.getId());
    return optimizeDto;
  }

}
