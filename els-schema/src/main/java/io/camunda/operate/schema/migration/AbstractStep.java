/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import java.time.OffsetDateTime;
import java.util.Objects;

public abstract class AbstractStep implements Step {

  private String content;
  private String description;
  private OffsetDateTime createdDate;
  private OffsetDateTime appliedDate;
  private String indexName;
  private boolean isApplied = false;
  private String version;
  private Integer order = 0;

  @Override
  public boolean isApplied() {
    return isApplied;
  }

  @Override
  public Step setApplied(final boolean isApplied) {
    this.isApplied = isApplied;
    return this;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public String getContent() {
    return content;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public Integer getOrder() {
    return order;
  }

  @Override
  public OffsetDateTime getCreatedDate() {
     if( createdDate == null) {
       createdDate = OffsetDateTime.now();
     }
     return createdDate;
  }

  @Override
  public Step setCreatedDate(final OffsetDateTime createDate) {
    this.createdDate = createDate;
    return this;
  }

  @Override
  public OffsetDateTime getAppliedDate() {
    return appliedDate;
  }

  @Override
  public Step setAppliedDate(final OffsetDateTime appliedDate) {
    this.appliedDate = appliedDate;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    AbstractStep that = (AbstractStep) o;
    return Objects.equals(indexName, that.indexName) && Objects.equals(version, that.version) && Objects.equals(order,
        that.order);
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexName, version, order);
  }

  @Override
  public String toString() {
    return "AbstractStep{" + "content='" + content + '\'' + ", description='" + description + '\'' + ", createdDate=" + createdDate + ", appliedDate=" + appliedDate + ", indexName='" + indexName + '\'' + ", isApplied=" + isApplied + ", version='" + version + '\'' + ", order=" + order + '}';
  }
}
