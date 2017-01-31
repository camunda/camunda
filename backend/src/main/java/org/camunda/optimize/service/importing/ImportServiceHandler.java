package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class ImportServiceHandler {

  private List<ImportService> services;

  @Autowired
  private ActivityImportService activityImportService;

  @Autowired
  private ProcessDefinitionImportService processDefinitionImportService;

  @Autowired
  private ProcessDefinitionXmlImportService processDefinitionXmlImportService;

  @PostConstruct
  public void init() {
    services = new ArrayList<>();
    services.add(activityImportService);
    services.add(processDefinitionImportService);
    services.add(processDefinitionXmlImportService);
  }

  public void executeProcessEngineImport() {
    for (ImportService service : services) {
      service.executeImport();
    }
  }
}
