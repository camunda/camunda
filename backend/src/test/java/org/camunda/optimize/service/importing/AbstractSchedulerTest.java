package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.index.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractSchedulerTest {

  protected List<PaginatedImportService> mockImportServices() throws OptimizeException {
    Map<String,PaginatedImportService> services = new HashMap<>();
    ActivityImportService activityImportService = mock(ActivityImportService.class);
    when(activityImportService.getElasticsearchType()).thenReturn("activity");
    services.put("activity", activityImportService);
    ProcessDefinitionImportService processDefinitionImportService = mock(ProcessDefinitionImportService.class);
    when(processDefinitionImportService.getElasticsearchType()).thenReturn("pd-is");
    services.put("pd-is",processDefinitionImportService);
    ProcessDefinitionXmlImportService processDefinitionXmlImportService = mock(ProcessDefinitionXmlImportService.class);
    when(processDefinitionXmlImportService.getElasticsearchType()).thenReturn("pd-xml");
    services.put("pd-xml",processDefinitionXmlImportService);
    for (PaginatedImportService service : services.values()) {
      ImportResult result = new ImportResult();
      when(service.executeImport(any())).thenReturn(result);
      if (service.isProcessDefinitionBased()) {
        DefinitionBasedImportIndexHandler handlerMock = Mockito.mock(DefinitionBasedImportIndexHandler.class);
        when(service.getImportIndexHandler()).thenReturn(handlerMock);
      } else {
        AllEntitiesBasedImportIndexHandler handlerMock = Mockito.mock(AllEntitiesBasedImportIndexHandler.class);
        when(service.getImportIndexHandler()).thenReturn(handlerMock);
      }
    }
    return new ArrayList<>(services.values());
  }
}
