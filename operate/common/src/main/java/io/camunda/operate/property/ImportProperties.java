/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

public class ImportProperties {

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191;

  /** Variable size under which we won't store preview separately. */
  private int variableSizeThreshold = DEFAULT_VARIABLE_SIZE_THRESHOLD;

  /**
   * When we build hierarchies for flow node instances (e.g. subprocess -> task inside subprocess)
   * and for process instances parent instance -> child instance), we normally read data only from
   * runtime indices. But it may occur that data was partially archived already. In this case import
   * process will be stuck with errors "Unable to find parent tree path for flow node instance" or
   * "Unable to find parent tree path for parent instance". This parameter allows to read parent
   * instances from archived indices. Should not be set true forever for performance reasons.
   */
  private boolean readArchivedParents = false;

  /**
   * When reading parent flow node instance from Elastic, we retry with 2 seconds delay for the case
   * when parent was imported with the previous batch but Elastic did not yet refresh the indices.
   * This may degrade import performance (especially when parent data is lost and no retry will help
   * to find it). In this case, disable the retry by setting the parameter to false.
   */
  private boolean retryReadingParents = true;

  public int getVariableSizeThreshold() {
    return variableSizeThreshold;
  }

  public ImportProperties setVariableSizeThreshold(final int variableSizeThreshold) {
    this.variableSizeThreshold = variableSizeThreshold;
    return this;
  }

  public boolean isReadArchivedParents() {
    return readArchivedParents;
  }

  public ImportProperties setReadArchivedParents(final boolean readArchivedParents) {
    this.readArchivedParents = readArchivedParents;
    return this;
  }

  public boolean isRetryReadingParents() {
    return retryReadingParents;
  }

  public ImportProperties setRetryReadingParents(final boolean retryReadingParents) {
    this.retryReadingParents = retryReadingParents;
    return this;
  }
}
