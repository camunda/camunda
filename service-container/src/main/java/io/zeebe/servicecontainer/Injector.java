/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer;

public class Injector<S> {
  protected S value;
  protected ServiceName<S> injectedServiceName;

  public void inject(S service) {
    this.value = service;
  }

  public void uninject() {
    this.value = null;
  }

  public S getValue() {
    return value;
  }

  public ServiceName<S> getInjectedServiceName() {
    return injectedServiceName;
  }

  public void setInjectedServiceName(ServiceName<S> injectedServiceName) {
    this.injectedServiceName = injectedServiceName;
  }
}
