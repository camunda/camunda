package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionImportService;
import org.camunda.optimize.service.importing.impl.ProcessDefinitionXmlImportService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "/applicationContext.xml" })
public class ImportServiceHandlerTest {

  @Autowired
  private ActivityImportService activityImportService;

  @Autowired
  private ProcessDefinitionImportService processDefinitionImportService;

  @Autowired
  private ProcessDefinitionXmlImportService processDefinitionXmlImportService;

  @Autowired
  private ImportServiceHandler importServiceHandler;

  @Test
  public void allImportsAreTriggered() {

    // when
    importServiceHandler.executeProcessEngineImport();

    // then
    Mockito.verify(activityImportService, Mockito.times(1)).executeImport();
    Mockito.verify(processDefinitionImportService, Mockito.times(1)).executeImport();
    Mockito.verify(processDefinitionXmlImportService, Mockito.times(1)).executeImport();
  }
}
