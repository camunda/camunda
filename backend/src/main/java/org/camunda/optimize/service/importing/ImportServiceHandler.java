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

  @Autowired
  private ImportServiceProvider importServiceProvider;

  public void executeProcessEngineImport() {
    for (ImportService service : importServiceProvider.getServices()) {
      service.executeImport();
    }
  }
}
