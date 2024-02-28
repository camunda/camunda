/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.tenant;

import java.util.concurrent.Callable;

public interface TenantAwareClient<REQ, RES> {

  public RES search(REQ searchRequest) throws Exception;

  public <C> C search(REQ searchRequest, Callable<C> searchExecutor) throws Exception;
}
