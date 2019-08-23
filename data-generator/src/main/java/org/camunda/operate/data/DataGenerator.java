/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data;

import org.camunda.operate.Shutdownable;

public interface DataGenerator extends Shutdownable{

  void createZeebeDataAsync(boolean manuallyCalled);
  
  public static final DataGenerator DO_NOTHING = new DataGenerator() {
    
    @Override
    public void shutdown() {
    }
    
    @Override
    public void createZeebeDataAsync(boolean manuallyCalled) {
    }
  };
}
