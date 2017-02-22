package org.camunda.optimize.test.factory;

import org.camunda.optimize.service.importing.ImportJobExecutor;
import org.camunda.optimize.service.importing.job.ImportJob;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.FactoryBean;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Import job executor factory used in unit tests in order to allow mocking
 * the execution of the import jobs.
 *
 * In particular, the job executor should not run in threads and the
 * jobs should be executed immediately when transferred to the executor.
 *
 * @author Johannes Heinemann
 */
public class MockImportJobExecutorFactory implements FactoryBean<ImportJobExecutor> {
  @Override
  public ImportJobExecutor getObject() throws Exception {
    ImportJobExecutor importJobExecutor = mock(ImportJobExecutor.class);

    // execute all import jobs immediately when committed
    Answer<Object> answer = invocationOnMock -> {
      ImportJob importJob = invocationOnMock.getArgument(0);
      importJob.run();
      return null;
    };
    doAnswer(answer).when(importJobExecutor).executeImportJob(any());

    return importJobExecutor;
  }

  @Override
  public Class<?> getObjectType() {
    return ImportJobExecutor.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
