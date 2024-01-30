/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.migration;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * A step implemented as elasticsearch ingest processor.<br>
 *
 * For comparing the steps it will be considered: indexName, version, order and content ,not dates and applied marker.
 */
@JsonTypeName("processorStep")
public class ProcessorStep extends AbstractStep {

  @Override
  public String toString() {
    return "ProcessorStep{" + "content='" + getContent() + '\'' + ", description='" + getDescription() + '\'' + ", createdDate="
        + getCreatedDate() + ", appliedDate=" + getAppliedDate() + ", indexName='" + getIndexName() + '\'' + ", isApplied="
        + isApplied() + ", version='" + getVersion() + '\'' + ", order=" + getOrder() + '}';
  }
}
