/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.service.SearchableIdentityCache;

public interface IdentityCacheSyncListener {

  void onFinishIdentitySync(SearchableIdentityCache newIdentityCache);
}
