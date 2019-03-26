package org.camunda.optimize.service.engine.importing.service;

import java.util.List;

public interface ImportService<T> {

  default void executeImport(List<T> pageOfEngineEntities) {
    executeImport(pageOfEngineEntities, () -> {
    });
  }

  void executeImport(List<T> pageOfEngineEntities, Runnable callback);
}
