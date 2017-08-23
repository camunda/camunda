package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.PaginatedImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.camunda.optimize.service.importing.index.AllEntitiesBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
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

  private static final String TEST_ENGINE = "1";


  protected List<PaginatedImportService> mockImportServices() throws OptimizeException {
    Map<String,PaginatedImportService> services = new HashMap<>();

    ActivityImportService activityImportService = mock(ActivityImportService.class);
    when(activityImportService.getElasticsearchType()).thenReturn("activity");
    when(activityImportService.getIndexHandlerType()).thenReturn(DefinitionBasedImportIndexHandler.class);
    services.put("activity", activityImportService);

    ProcessDefinitionImportService processDefinitionImportService = mock(ProcessDefinitionImportService.class);
    when(processDefinitionImportService.getElasticsearchType()).thenReturn("pd-is");
    when(processDefinitionImportService.getIndexHandlerType()).thenReturn(AllEntitiesBasedImportIndexHandler.class);
    services.put("pd-is",processDefinitionImportService);

    ProcessDefinitionXmlImportService processDefinitionXmlImportService = mock(ProcessDefinitionXmlImportService.class);
    when(processDefinitionXmlImportService.getElasticsearchType()).thenReturn("pd-xml");
    when(processDefinitionXmlImportService.getIndexHandlerType()).thenReturn(AllEntitiesBasedImportIndexHandler.class);
    services.put("pd-xml",processDefinitionXmlImportService);

    for (PaginatedImportService service : services.values()) {
      ImportResult result = new ImportResult();
      result.setIndexHandlerType(service.getIndexHandlerType());
      result.setElasticSearchType(service.getElasticsearchType());
      when(service.executeImport(any())).thenReturn(result);
    }
    return new ArrayList<>(services.values());
  }

  protected void mockIndexHandlers(List<PaginatedImportService> services, IndexHandlerProvider indexHandlerProvider) {
    List<ImportIndexHandler> allMocks = new ArrayList<>();
    for (PaginatedImportService service : services) {
      ImportIndexHandler handlerMock = Mockito.mock(ImportIndexHandler.class);
      when(indexHandlerProvider.getIndexHandler(
          service.getElasticsearchType(), service.getIndexHandlerType(), TEST_ENGINE)
      ).thenReturn(handlerMock);


      allMocks.add(handlerMock);
    }

    when(indexHandlerProvider.getAllHandlers()).thenReturn(allMocks);
  }
}
