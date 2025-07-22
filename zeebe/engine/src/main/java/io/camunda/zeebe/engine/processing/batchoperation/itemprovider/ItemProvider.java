/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import java.util.List;

public interface ItemProvider {

  ItemPage fetchItemPage(String cursor, int pageSize);

  default SecurityContext createSecurityContext(
      final CamundaAuthentication authentication, final Authorization authorization) {
    return SecurityContext.of(
        b -> b.withAuthentication(authentication).withAuthorization(authorization));
  }

  /**
   * Internal abstraction to hold an item of a batch operation. This is used to represent an itemKey
   * and it's related processInstanceKey.
   *
   * @param itemKey the key of the item
   * @param processInstanceKey the key of the process instance this item belongs to
   */
  record Item(long itemKey, long processInstanceKey) {}

  /**
   * Internal abstraction to hold the result of a page of entity items.
   *
   * @param items the fetched items
   * @param endCursor cursor to fetch the next page of items
   * @param total the total amount of found items
   * @param isLastPage indicates if this is the last page of items
   */
  record ItemPage(List<Item> items, String endCursor, long total, boolean isLastPage) {}
}
