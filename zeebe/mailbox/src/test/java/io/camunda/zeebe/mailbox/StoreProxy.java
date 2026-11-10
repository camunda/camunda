/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/** Wraps a store to record the calls to it and to make chosen methods fail. */
final class StoreProxy {

  private StoreProxy() {}

  /**
   * @param delegate the store that does the work
   * @param failingMethods names of the methods that throw a {@link MailboxStoreException}
   * @param calls receives the name of each method that is called, in order
   */
  static MailboxStore wrap(
      final MailboxStore delegate, final Set<String> failingMethods, final List<String> calls) {
    return (MailboxStore)
        Proxy.newProxyInstance(
            MailboxStore.class.getClassLoader(),
            new Class<?>[] {MailboxStore.class},
            (proxy, method, args) -> {
              calls.add(method.getName());
              if (failingMethods.contains(method.getName())) {
                throw new MailboxStoreException("injected failure", new RuntimeException());
              }
              try {
                return method.invoke(delegate, args);
              } catch (final InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }
}
