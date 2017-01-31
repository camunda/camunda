package org.camunda.optimize.service.importing;

/**
 * Every class that should import data from the
 * engine needs to implement this interface and
 * add themself to the {@link ImportServiceHandler}.
 */
public interface ImportService {

  void executeImport();
}
