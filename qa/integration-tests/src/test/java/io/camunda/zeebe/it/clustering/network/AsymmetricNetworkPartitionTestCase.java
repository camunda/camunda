/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.network;

import io.camunda.zeebe.client.ZeebeClient;
import java.util.concurrent.CompletableFuture;

interface AsymmetricNetworkPartitionTestCase {

  /**
   * The given part is called before the asymmetric partition is set up. This can be used by the
   * test case to setup own resources or for example deploy processes etc.
   *
   * @param client the zeebe client which can be used by the test case
   */
  void given(ZeebeClient client);

  /**
   * The when part is called when the asymmetric partition is set up. This can be used by the test
   * case to start certain things, which might complete later, for that case the a future can be
   * returned. It is allowed to return null here, if there is nothing to wait for.
   *
   * @param client the zeebe client which can be used by the test case
   * @return the future which can be awaited in the then part, or null
   */
  CompletableFuture<?> when(ZeebeClient client);

  /**
   * The then part is called when the asymmetric partition is taken down again and we expect that
   * everything should work again. Here the test can verify whether things are distributed or
   * completed.
   *
   * <p>For example with the given future, which might be created by the when part.
   *
   * @param client the zeebe client which can be used by the test case
   * @param whenFuture the future which can be awaited, or null
   */
  void then(ZeebeClient client, CompletableFuture<?> whenFuture);
}
