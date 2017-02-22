package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.job.ImportJob;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/applicationContext.xml"})
public class ImportJobExecutorTest {

  @Autowired
  private ImportJobExecutor importJobExecutor;

  @Test
  public void importJobIsBeingExecuted() throws Exception {
    // given
    ImportJob importJob = mock(ImportJob.class);

    // when
    importJobExecutor.executeImportJob(importJob);

    // then
    verify(importJob, times(1)).run();
  }

}
