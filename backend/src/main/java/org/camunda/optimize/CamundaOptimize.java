package org.camunda.optimize;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;

/**
 * @author Askar Akhmerov
 */
public interface CamundaOptimize {

  ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor();

  void startImportSchedulers();

  void disableImportSchedulers();
}
