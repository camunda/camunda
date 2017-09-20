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
import org.camunda.optimize.service.importing.job.schedule.PageBasedImportScheduleJob;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashMap;
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

  private Map<String, ImportService> allServices = new HashMap<>();
  private Map<String, PaginatedImportService> paginatedServices = new HashMap<>();

  @PostConstruct
  public void init() {
    paginatedServices.put(getProcessDefinitionImportService().getElasticsearchType(), getProcessDefinitionImportService());
    paginatedServices.put(getProcessDefinitionXmlImportService().getElasticsearchType(), getProcessDefinitionXmlImportService());
    paginatedServices.put(activityImportService.getElasticsearchType(), activityImportService);

    allServices.put(getProcessDefinitionImportService().getElasticsearchType(), getProcessDefinitionImportService());
    allServices.put(getProcessDefinitionXmlImportService().getElasticsearchType(), getProcessDefinitionXmlImportService());
    allServices.put(activityImportService.getElasticsearchType(), activityImportService);
    allServices.put(variableImportService.getElasticsearchType(), variableImportService);
    allServices.put(processInstanceImportService.getElasticsearchType(), processInstanceImportService);
  }

  private PaginatedImportService getProcessDefinitionImportService() {
    if (configurationService.areProcessDefinitionsToImportDefined()) {
      return processDefinitionIdBasedImportService;
    } else {
      return processDefinitionImportService;
    }
  }

  private PaginatedImportService getProcessDefinitionXmlImportService() {
    if (configurationService.areProcessDefinitionsToImportDefined()) {
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
    return paginatedServices.values();
  }

  public PaginatedImportService getPaginatedImportService(String elasticsearchType) {
    return paginatedServices.get(elasticsearchType);
  }

  public ImportService getImportService(String elasticsearchType) {
    return allServices.get(elasticsearchType);
  }
}
