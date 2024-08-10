/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import java.util.List;

public class ArchiveBatch {

  private String finishDate;
  private List<Object> ids;

  public ArchiveBatch(String finishDate, List<Object> ids) {
    this.finishDate = finishDate;
    this.ids = ids;
  }

  public String getFinishDate() {
    return finishDate;
  }

  public void setFinishDate(String finishDate) {
    this.finishDate = finishDate;
  }

  public List<Object> getIds() {
    return ids;
  }

  public void setIds(List<Object> ids) {
    this.ids = ids;
  }

  @Override
  public String toString() {
    return "ArchiveBatch{" + "finishDate='" + finishDate + '\'' + ", ids=" + ids + '}';
  }
}
