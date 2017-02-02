package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class ImportServiceHandlerTest {

  @Autowired
  private ImportServiceProvider importServiceProvider;

  @Autowired
  private ImportServiceHandler importServiceHandler;

  @Test
  public void allImportsAreTriggered() {

    // given
    List<ImportService> services = mockImportServices();
    when(importServiceProvider.getServices()).thenReturn(services);

    // when
    importServiceHandler.executeProcessEngineImport();

    // then
    for (ImportService service : services) {
      verify(service, times(1)).executeImport();
    }
  }

  private List<ImportService> mockImportServices() {
    List<ImportService> services = new ArrayList<>();
    services.add(mock(ActivityImportService.class));
    services.add(mock(ProcessDefinitionImportService.class));
    services.add(mock(ProcessDefinitionXmlImportService.class));
    return services;
  }
}
