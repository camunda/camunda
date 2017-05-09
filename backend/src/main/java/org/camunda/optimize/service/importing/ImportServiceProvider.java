package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.impl.ProcessInstanceImportService;
import org.camunda.optimize.service.importing.impl.VariableImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImportServiceProvider {

  @Autowired
  private ProcessInstanceImportService processInstanceImportService;

  @Autowired
  private ActivityImportService activityImportService;

  @Autowired
  private ProcessDefinitionImportService processDefinitionImportService;

  @Autowired
  private ProcessDefinitionXmlImportService processDefinitionXmlImportService;

  private List<PaginatedImportService> services;

  @PostConstruct
  public void init() {
    services = new ArrayList<>();
    services.add(activityImportService);
    services.add(processDefinitionImportService);
    services.add(processDefinitionXmlImportService);
  }

  public ProcessInstanceImportService getProcessInstanceImportService() {
    return processInstanceImportService;
  }

  public List<PaginatedImportService> getPagedServices() {
    return services;
  }
   
}
