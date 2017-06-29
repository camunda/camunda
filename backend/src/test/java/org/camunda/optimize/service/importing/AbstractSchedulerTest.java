package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractSchedulerTest {

  protected List<PaginatedImportService> mockImportServices() throws OptimizeException {
    ArrayList<PaginatedImportService> services = new ArrayList<>();
    ActivityImportService activityImportService = mock(ActivityImportService.class);
    when(activityImportService.getElasticsearchType()).thenReturn("activity");
    services.add(activityImportService);
    ProcessDefinitionImportService processDefinitionImportService = mock(ProcessDefinitionImportService.class);
    when(processDefinitionImportService.getElasticsearchType()).thenReturn("pd-is");
    services.add(processDefinitionImportService);
    ProcessDefinitionXmlImportService processDefinitionXmlImportService = mock(ProcessDefinitionXmlImportService.class);
    when(processDefinitionXmlImportService.getElasticsearchType()).thenReturn("pd-xml");
    services.add(processDefinitionXmlImportService);
    for (PaginatedImportService service : services) {
      when(service.executeImport()).thenReturn(new ImportResult());
    }
    return services;
  }
}
