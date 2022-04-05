/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * A step implemented as elasticsearch ingest processor.<br>
 *
 * For comparing the steps it will be considered: indexName, version, order and content ,not dates and applied marker.
 */
@JsonTypeName("processorStep")
public class ProcessorStep implements Step {

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
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((content == null) ? 0 : content.hashCode());
    result = prime * result + ((indexName == null) ? 0 : indexName.hashCode());
    result = prime * result + ((order == null) ? 0 : order.hashCode());
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ProcessorStep other = (ProcessorStep) obj;
    if (content == null) {
      if (other.content != null)
        return false;
    } else if (!content.equals(other.content))
      return false;
    if (indexName == null) {
      if (other.indexName != null)
        return false;
    } else if (!indexName.equals(other.indexName))
      return false;
    if (order == null) {
      if (other.order != null)
        return false;
    } else if (!order.equals(other.order))
      return false;
    if (version == null) {
      if (other.version != null)
        return false;
    } else if (!version.equals(other.version))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "ProcessorStep [content=" + content + ", appliedDate=" + appliedDate + ", indexName="
        + indexName + ", isApplied=" + isApplied + ", version=" + version + ", order=" + order
        + ", createdDate=" + getCreatedDate() + "]";
  }

}
