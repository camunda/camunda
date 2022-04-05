/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.dao.response;

import org.jetbrains.annotations.NotNull;

public class InsertResponse implements DAOResponse {

  private boolean error;

  public static InsertResponse success() {
    return buildInsertResponse(false);
  }

  public static InsertResponse failure() {
    return buildInsertResponse(true);
  }

  @NotNull
  private static InsertResponse buildInsertResponse(boolean error) {
    final InsertResponse insertResponse = new InsertResponse();
    insertResponse.error = error;
    return insertResponse;
  }

  @Override
  public boolean hasError() {
    return error;
  }
}
