/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.tenant;

import java.util.Collection;

public interface TenantCheckApplier<T> {

  void apply(final T searchRequest);

  void apply(final T searchRequest, Collection<String> tenantIds);
}
