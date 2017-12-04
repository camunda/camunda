package org.camunda.optimize.service.engine.importing.job.factory;

import java.util.Optional;


public interface EngineImportJobFactory {

  Optional<Runnable> getNextJob();

  long getBackoffTimeInMs();
}
