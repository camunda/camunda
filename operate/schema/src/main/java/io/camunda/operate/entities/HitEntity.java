/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

public class HitEntity {
  String index;

  String sourceAsString;

  public String getSourceAsString() {
    return sourceAsString;
  }

  public HitEntity setSourceAsString(String sourceAsString) {
    this.sourceAsString = sourceAsString;
    return this;
  }

  public String getIndex() {
    return index;
  }

  public HitEntity setIndex(String index) {
    this.index = index;
    return this;
  }
}
