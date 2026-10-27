/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.Limiter.Listener;

class NoopListener implements Listener {
  public static final NoopListener INSTANCE = new NoopListener();

  @Override
  public void onSuccess() {}

  @Override
  public void onIgnore() {}

  @Override
  public void onDropped() {}
}
