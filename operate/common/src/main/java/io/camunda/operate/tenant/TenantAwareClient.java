/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.tenant;

import java.util.concurrent.Callable;

public interface TenantAwareClient<REQ, RES> {

  public RES search(REQ searchRequest) throws Exception;

  public <C> C search(REQ searchRequest, Callable<C> searchExecutor) throws Exception;
}
