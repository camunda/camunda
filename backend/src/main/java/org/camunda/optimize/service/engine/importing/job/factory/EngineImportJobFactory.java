package org.camunda.optimize.service.engine.importing.job.factory;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;


public interface EngineImportJobFactory {

  Optional<Runnable> getNextJob();

  long getBackoffTimeInMs();
}
