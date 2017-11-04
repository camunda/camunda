package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;

import java.util.Optional;

public interface EngineImportJobFactory {

  Optional<Runnable> getNextJob();

  long getBackoffTimeInMs();

  void setElasticsearchImportExecutor(ElasticsearchImportJobExecutor elasticsearchImportJobExecutor);
}
