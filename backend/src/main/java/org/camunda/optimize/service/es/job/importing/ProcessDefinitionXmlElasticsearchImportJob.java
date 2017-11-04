package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionXmlElasticsearchImportJob extends ElasticsearchImportJob<ProcessDefinitionXmlOptimizeDto> {

  private ProcessDefinitionWriter processDefinitionWriter;
  private Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlElasticsearchImportJob.class);

  public ProcessDefinitionXmlElasticsearchImportJob(ProcessDefinitionWriter processDefinitionWriter) {
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  protected void executeImport() {
    try {
      processDefinitionWriter.importProcessDefinitionXmls(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }
}
