/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch.dao.response;

public class InsertResponse implements DAOResponse {

  private boolean error;

  public static InsertResponse success() {
    return buildInsertResponse(false);
  }

  public static InsertResponse failure() {
    return buildInsertResponse(true);
  }

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
