package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionXmlOptimizeDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ProcessDefinitionXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionXmlElasticsearchImportJob extends ElasticsearchImportJob<ProcessDefinitionXmlOptimizeDto> {

  private ProcessDefinitionXmlWriter processDefinitionXmlWriter;
  private Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlElasticsearchImportJob.class);

  public ProcessDefinitionXmlElasticsearchImportJob(ProcessDefinitionXmlWriter processDefinitionXmlWriter) {
    this.processDefinitionXmlWriter = processDefinitionXmlWriter;
  }

  @Override
  protected void executeImport() {
    try {
      processDefinitionXmlWriter.importProcessDefinitionXmls(newOptimizeEntities);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }
}
