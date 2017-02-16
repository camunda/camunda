package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.diff.MissingEntriesFinder;
import org.camunda.optimize.service.importing.diff.MissingProcessDefinitionXmlFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProcessDefinitionXmlImportService extends PaginatedImportService<ProcessDefinitionXmlEngineDto, ProcessDefinitionXmlOptimizeDto> {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlImportService.class);

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private MissingProcessDefinitionXmlFinder xmlFinder;

  @Override
  protected MissingEntriesFinder<ProcessDefinitionXmlEngineDto> getMissingEntriesFinder() {
    return xmlFinder;
  }

  @Override
  public void importToElasticSearch(List<ProcessDefinitionXmlOptimizeDto> newOptimizeEntriesToImport) {
    try {
      procDefWriter.importProcessDefinitionXmls(newOptimizeEntriesToImport);
    } catch (Exception e) {
      logger.error("error while writing process definition xmls to elasticsearch", e);
    }
  }

  @Override
  public List<ProcessDefinitionXmlOptimizeDto> mapToOptimizeDto(List<ProcessDefinitionXmlEngineDto> entries) {
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
