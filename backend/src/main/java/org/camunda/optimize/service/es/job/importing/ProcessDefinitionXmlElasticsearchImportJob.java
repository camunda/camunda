package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProcessDefinitionXmlElasticsearchImportJob extends ElasticsearchImportJob<ProcessDefinitionOptimizeDto> {

  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;

  public ProcessDefinitionXmlElasticsearchImportJob(ProcessDefinitionXmlWriter processDefinitionXmlWriter) {
    super(() -> {});
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
  }

  @Override
  protected void persistEntities(List<ProcessDefinitionOptimizeDto> newOptimizeEntities) throws Exception {
    processDefinitionXmlWriter.importProcessDefinitionXmls(newOptimizeEntities);
  }
}
