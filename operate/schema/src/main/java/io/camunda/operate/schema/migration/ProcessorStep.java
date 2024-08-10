/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * A step implemented as elasticsearch ingest processor.<br>
 * For comparing the steps it will be considered: indexName, version, order and content ,not dates
 * and applied marker.
 */
@JsonTypeName("processorStep")
public class ProcessorStep extends AbstractStep {

  @Override
  public String toString() {
    return "ProcessorStep{"
        + "content='"
        + getContent()
        + '\''
        + ", description='"
        + getDescription()
        + '\''
        + ", createdDate="
        + getCreatedDate()
        + ", appliedDate="
        + getAppliedDate()
        + ", indexName='"
        + getIndexName()
        + '\''
        + ", isApplied="
        + isApplied()
        + ", version='"
        + getVersion()
        + '\''
        + ", order="
        + getOrder()
        + '}';
  }
}
