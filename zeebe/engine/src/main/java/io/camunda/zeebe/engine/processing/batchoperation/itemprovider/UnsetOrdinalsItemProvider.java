/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

public class UnsetOrdinalsItemProvider implements ItemProvider {
  private final ItemProvider delegate;

  public UnsetOrdinalsItemProvider(final ItemProvider delegate) {
    this.delegate = delegate;
  }

  @Override
  public ItemPage fetchItemPage(final String cursor, final int pageSize) {
    final var page = delegate.fetchItemPage(cursor, pageSize);
    if (page != null) {
      return new ItemPage(
          page.items().stream()
              .map(
                  item ->
                      new Item(
                          item.itemKey(),
                          item.processInstanceKey(),
                          item.rootProcessInstanceKey(),
                          null))
              .toList(),
          page.endCursor(),
          page.total(),
          page.isLastPage());
    }
    return null;
  }
}
