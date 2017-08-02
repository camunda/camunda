package org.camunda.optimize.service.importing.provider;

import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionIdBasedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlIdBasedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.impl.ProcessInstanceImportService;
import org.camunda.optimize.service.importing.impl.VariableImportService;
import org.camunda.optimize.service.util.ConfigurationReloadable;
import org.camunda.optimize.service.util.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ImportServiceProvider implements ConfigurationReloadable {

  @Autowired
  private ProcessInstanceImportService processInstanceImportService;

  @Autowired
  private VariableImportService variableImportService;

  @Autowired
  private ActivityImportService activityImportService;

  @Autowired
  private ProcessDefinitionImportService processDefinitionImportService;

  @Autowired
  private ProcessDefinitionXmlImportService processDefinitionXmlImportService;

  @Autowired
  private ProcessDefinitionXmlIdBasedImportService processDefinitionXmlIdBasedImportService;

  @Autowired
  private ProcessDefinitionIdBasedImportService processDefinitionIdBasedImportService;

  @Autowired
  private ConfigurationService configurationService;

  private Map<String,PaginatedImportService> services;

  @PostConstruct
  public void init() {
    services = new HashMap<>();
    services.put(getProcessDefinitionImportService().getElasticsearchType(), getProcessDefinitionImportService());
    services.put(getProcessDefinitionXmlImportService().getElasticsearchType(), getProcessDefinitionXmlImportService());
    services.put(activityImportService.getElasticsearchType(),activityImportService);
  }

  private PaginatedImportService getProcessDefinitionImportService() {
    if( configurationService.areProcessDefinitionsToImportDefined()) {
      return processDefinitionIdBasedImportService;
    } else {
      return processDefinitionImportService;
    }
  }

  private PaginatedImportService getProcessDefinitionXmlImportService() {
    if( configurationService.areProcessDefinitionsToImportDefined()) {
      return processDefinitionXmlIdBasedImportService;
    } else {
      return processDefinitionXmlImportService;
    }
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    init();
  }

  public ProcessInstanceImportService getProcessInstanceImportService() {
    return processInstanceImportService;
  }

  public VariableImportService getVariableImportService() {
    return variableImportService;
  }

  public Collection<PaginatedImportService> getPagedServices() {
    return services.values();
  }

  public PaginatedImportService getImportService(String elasticsearchType) {
    return services.get(elasticsearchType);
  }
}
