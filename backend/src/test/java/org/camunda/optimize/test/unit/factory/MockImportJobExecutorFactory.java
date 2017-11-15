package org.camunda.optimize.test.unit.factory;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
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
public class MockImportJobExecutorFactory implements FactoryBean<ElasticsearchImportJobExecutor> {
  @Override
  public ElasticsearchImportJobExecutor getObject() throws Exception {
    ElasticsearchImportJobExecutor elasticsearchImportJobExecutor = mock(ElasticsearchImportJobExecutor.class);

    // execute all import jobs immediately when committed
    Answer<Object> answer = invocationOnMock -> {
      ElasticsearchImportJob elasticsearchImportJob = invocationOnMock.getArgument(0);
      elasticsearchImportJob.run();
      return null;
    };
    doAnswer(answer).when(elasticsearchImportJobExecutor).executeImportJob(any());

    return elasticsearchImportJobExecutor;
  }

  @Override
  public Class<?> getObjectType() {
    return ElasticsearchImportJobExecutor.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
