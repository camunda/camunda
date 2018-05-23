package org.camunda.optimize;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;


public interface CamundaOptimize {

  ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor();

  void startImportSchedulers();

  void disableImportSchedulers();

  void enableImportSchedulers();
}
