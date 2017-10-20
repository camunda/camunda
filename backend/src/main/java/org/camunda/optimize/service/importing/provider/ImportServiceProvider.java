package org.camunda.optimize.service.importing.provider;

import org.camunda.optimize.service.importing.ImportService;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.FinishedProcessInstanceImportService;
import org.camunda.optimize.service.importing.impl.IdBasedProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.IdBasedProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.impl.ProcessInstanceImportService;
import org.camunda.optimize.service.importing.impl.VariableImportService;
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

  public static final String UNFINISHED_PROCESS_INSTANCE_TYPE = "unfinished-process-instance";

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private ConfigurationService configurationService;

  //ImportServices grouped by engine and type
  private Map<String, Map<String, ImportService>> allServices = new HashMap<>();
  //PaginatedImportService grouped by engine and type
  private Map<String, Map<String, PaginatedImportService>> paginatedServices = new HashMap<>();

  @PostConstruct
  public void init() {
    for (String engineAlias : configurationService.getConfiguredEngines().keySet()) {
      Map<String, PaginatedImportService> engineServices = new HashMap<>();

      PaginatedImportService processDefinitionImportService = getProcessDefinitionImportService(engineAlias);
      PaginatedImportService processDefinitionXmlImportService = getProcessDefinitionXmlImportService(engineAlias);
      ActivityImportService activityImportService = getActivityImportService(engineAlias);
      VariableImportService variableImportService = getVariableImportService(engineAlias);
      FinishedProcessInstanceImportService processInstanceImportService = getFinishedProcessInstanceImportService(engineAlias);

      engineServices.put(processDefinitionImportService.getElasticsearchType(), processDefinitionImportService);
      engineServices.put(processDefinitionXmlImportService.getElasticsearchType(), processDefinitionXmlImportService);
      engineServices.put(activityImportService.getElasticsearchType(), activityImportService);
      engineServices.put(variableImportService.getElasticsearchType(), variableImportService);
      engineServices.put(processInstanceImportService.getElasticsearchType(), processInstanceImportService);

      paginatedServices.put(engineAlias, engineServices);

      Map<String, ImportService> importServiceMap = new HashMap<>();
      importServiceMap.put(processDefinitionImportService.getElasticsearchType(), processDefinitionImportService);
      importServiceMap.put(processDefinitionXmlImportService.getElasticsearchType(), processDefinitionXmlImportService);
      importServiceMap.put(activityImportService.getElasticsearchType(), activityImportService);
      importServiceMap.put(variableImportService.getElasticsearchType(), variableImportService);
      importServiceMap.put(processInstanceImportService.getElasticsearchType(), processInstanceImportService);
      importServiceMap.put(UNFINISHED_PROCESS_INSTANCE_TYPE, getUnfinishedProcessInstanceImportService(engineAlias));

      allServices.put(engineAlias, importServiceMap);
    }

  }

  private ActivityImportService getActivityImportService(String engineAlias) {
    ActivityImportService result;
    if (isInstantiated(engineAlias, configurationService.getEventType())) {
      result = (ActivityImportService) this.allServices.get(engineAlias).get(configurationService.getEventType());
    } else {
      result = new ActivityImportService(engineAlias);
      applicationContext.getAutowireCapableBeanFactory().autowireBean(result);
    }
    return result;
  }

  public FinishedProcessInstanceImportService getFinishedProcessInstanceImportService(String engineAlias) {
    FinishedProcessInstanceImportService result;
    if (isInstantiated(engineAlias, configurationService.getProcessInstanceType())) {
      result = (FinishedProcessInstanceImportService) this.allServices.get(engineAlias).get(configurationService.getProcessInstanceType());
    } else {
      result = new FinishedProcessInstanceImportService(engineAlias);
      applicationContext.getAutowireCapableBeanFactory().autowireBean(result);
    }
    return result;
  }

  private boolean isInstantiated(String engineAlias, String eventType) {
    return this.allServices.get(engineAlias) != null &&
        this.allServices.get(engineAlias).get(eventType) != null;
  }

  private PaginatedImportService getProcessDefinitionImportService(String engineAlias) {
    PaginatedImportService result;

    if (isInstantiated(engineAlias, configurationService.getProcessDefinitionType())) {
      result = (PaginatedImportService) this.allServices.get(engineAlias).get(configurationService.getProcessDefinitionType());
    } else {
      if (configurationService.areProcessDefinitionsToImportDefined()) {
        result = new IdBasedProcessDefinitionImportService(engineAlias);
      } else {
        result = new ProcessDefinitionImportService(engineAlias);
      }
      applicationContext.getAutowireCapableBeanFactory().autowireBean(result);
    }

    return result;
  }

  private PaginatedImportService getProcessDefinitionXmlImportService(String engineAlias) {
    PaginatedImportService result;

    if (isInstantiated(engineAlias, configurationService.getProcessDefinitionXmlType())) {
      result = (PaginatedImportService) this.allServices.get(engineAlias).get(configurationService.getProcessDefinitionXmlType());

    } else {
      if (configurationService.areProcessDefinitionsToImportDefined()) {
        result = new IdBasedProcessDefinitionXmlImportService(engineAlias);
      } else {
        result = new ProcessDefinitionXmlImportService(engineAlias);
      }
      applicationContext.getAutowireCapableBeanFactory().autowireBean(result);
    }

    return result;
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    allServices = new HashMap<>();
    paginatedServices = new HashMap<>();
    init();
  }

  public ProcessInstanceImportService getUnfinishedProcessInstanceImportService(String engineAlias) {
    ProcessInstanceImportService result;
    if (isInstantiated(engineAlias, UNFINISHED_PROCESS_INSTANCE_TYPE)) {
      result = (ProcessInstanceImportService) this.allServices.get(engineAlias).get(UNFINISHED_PROCESS_INSTANCE_TYPE);
    } else {
      result = new ProcessInstanceImportService(engineAlias);
      applicationContext.getAutowireCapableBeanFactory().autowireBean(result);
    }
    return result;
  }

  public VariableImportService getVariableImportService(String engineAlias) {
    VariableImportService result;
    if (isInstantiated(engineAlias, configurationService.getProcessInstanceType())) {
      result = (VariableImportService) this.allServices.get(engineAlias).get(configurationService.getVariableType());
    } else {
      result = new VariableImportService(engineAlias);
      applicationContext.getAutowireCapableBeanFactory().autowireBean(result);
    }
    return result;
  }

  public Collection<PaginatedImportService> getPagedServices(String engine) {
    Collection<PaginatedImportService> result = null;
    if (paginatedServices.get(engine) != null) {
      result = paginatedServices.get(engine).values();
    }
    return result;
  }

  public PaginatedImportService getPaginatedImportService(String elasticsearchType, String engine) {
    return paginatedServices.get(engine).get(elasticsearchType);
  }

  public ImportService getImportService(String elasticsearchType, String engineAlias) {
    return getAllEngineServices(engineAlias).get(elasticsearchType);
  }

  public Map<String, ImportService> getAllEngineServices(String engineAlias) {
    return allServices.get(engineAlias);
  }

  public Map<String, Map<String, ImportService>> getAllEngineServices() {
    return allServices;
  }
}
