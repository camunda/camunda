/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

public interface ImportListener {

  public void finished(int count);
  public void failed(int count);

  public class Compound implements ImportListener{

    private ImportListener[] delegates;

    public Compound(ImportListener ...listeners) {
      this.delegates = listeners;
    }
    @Override
    public void finished(int count) {
      for(ImportListener delegate: delegates) {
        delegate.finished(count);
      }
    }

    @Override
    public void failed(int count) {
      for(ImportListener delegate: delegates) {
        delegate.failed(count);
      }
    }
    
  }
}
