package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.service.es.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Component
public class ProcessDefinitionImportService implements ImportService {

  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionImportService.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private Client client;

  @Override
  public void executeImport() {
    executeProcessDefinitionImport();
  }

  private void executeProcessDefinitionImport() {

    List<ProcessDefinitionDto> entries = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
      .path(configurationService.getProcessDefinitionEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<ProcessDefinitionDto>>() {
      });

    try {
      procDefWriter.importProcessDefinitions(entries);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }
}
