package org.camunda.optimize.service.importing.impl;

import org.camunda.optimize.dto.engine.ProcessDefinitionDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlDto;
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
public class ProcessDefinitionXmlImportService implements ImportService {
  private final Logger logger = LoggerFactory.getLogger(ProcessDefinitionXmlImportService.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ProcessDefinitionWriter procDefWriter;

  @Autowired
  private Client client;


  @Override
  public void executeImport() {
    executeProcessDefinitionXmlImport();
  }

  private void executeProcessDefinitionXmlImport() {
    List<ProcessDefinitionDto> entries = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
      .path(configurationService.getProcessDefinitionEndpoint())
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<List<ProcessDefinitionDto>>() {
      });
    for (ProcessDefinitionDto entry : entries) {
      executeProcessDefinitionXmlImport(entry.getId());
    }
  }

  private void executeProcessDefinitionXmlImport(String processDefinitionId) {

    ProcessDefinitionXmlDto entry = client
      .target(configurationService.getEngineRestApiEndpoint() + configurationService.getEngineName())
      .path(configurationService.getProcessDefinitionXmlEndpoint(processDefinitionId))
      .request(MediaType.APPLICATION_JSON)
      .get(new GenericType<ProcessDefinitionXmlDto>() {
      });

    try {
      procDefWriter.importProcessDefinitionXmls(entry);
    } catch (Exception e) {
      logger.error("error while writing process definitions to elasticsearch", e);
    }
  }

  public Client getClient() {
    return client;
  }

  public void setClient(Client client) {
    this.client = client;
  }
}
