/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.response;

public enum SnapshotState {
  FAILED("FAILED"),
  PARTIAL("PARTIAL"),
  STARTED("STARTED"),
  SUCCESS("SUCCESS");
  private final String state;

  SnapshotState(final String state) {
    this.state = state;
  }

  @Override
  public String toString() {
    return state;
  }
}
