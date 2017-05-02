package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.impl.VariableImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImportServiceProvider {

  @Autowired
  private ActivityImportService activityImportService;

  @Autowired
  private ProcessDefinitionImportService processDefinitionImportService;

  @Autowired
  private ProcessDefinitionXmlImportService processDefinitionXmlImportService;

  @Autowired
  private VariableImportService variableImportService;

  private List<ImportService> services;

  @PostConstruct
  public void init() {
    services = new ArrayList<>();
    services.add(activityImportService);
    services.add(processDefinitionImportService);
    services.add(processDefinitionXmlImportService);
  }

  public List<ImportService> getServices() {
    return services;
  }
}
