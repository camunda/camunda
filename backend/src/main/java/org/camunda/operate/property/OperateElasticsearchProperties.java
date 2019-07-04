/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.property;

public class OperateElasticsearchProperties extends ElasticsearchProperties {

  public static final String DEFAULT_INDEX_PREFIX = "operate";

  private String indexPrefix = DEFAULT_INDEX_PREFIX;

  private int templateOrder = 30;

  private boolean rolloverEnabled = true;

  /**
   * This format will be used to create timed indices. It must correspond to rolloverInterval parameter.
   */
  private String rolloverDateFormat = "yyyyMMdd";
  private String elsRolloverDateFormat = "basic_date";
  /**
   * Interval description for "date histogram" aggregation, which is used to group finished instances.
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-datehistogram-aggregation.html">Elasticsearch docs</a>
   */
  private String rolloverInterval = "1d";

  private int rolloverBatchSize = 100;

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public int getTemplateOrder() {
    return templateOrder;
  }

  public void setTemplateOrder(int templateOrder) {
    this.templateOrder = templateOrder;
  }

  public boolean isRolloverEnabled() {
    return rolloverEnabled;
  }

  public void setRolloverEnabled(boolean rolloverEnabled) {
    this.rolloverEnabled = rolloverEnabled;
  }

  public String getRolloverDateFormat() {
    return rolloverDateFormat;
  }

  public void setRolloverDateFormat(String rolloverDateFormat) {
    this.rolloverDateFormat = rolloverDateFormat;
  }

  public String getElsRolloverDateFormat() {
    return elsRolloverDateFormat;
  }

  public void setElsRolloverDateFormat(String elsRolloverDateFormat) {
    this.elsRolloverDateFormat = elsRolloverDateFormat;
  }

  public String getRolloverInterval() {
    return rolloverInterval;
  }

  public void setRolloverInterval(String rolloverInterval) {
    this.rolloverInterval = rolloverInterval;
  }

  public int getRolloverBatchSize() {
    return rolloverBatchSize;
  }

  public void setRolloverBatchSize(int rolloverBatchSize) {
    this.rolloverBatchSize = rolloverBatchSize;
  }

}
