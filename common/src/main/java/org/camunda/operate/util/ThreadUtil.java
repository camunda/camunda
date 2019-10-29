/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadUtil {
  
  private static final Logger logger = LoggerFactory.getLogger(ThreadUtil.class);

  public static void sleepFor(long milliseconds) {
     sleepForAndShouldInterrupt(milliseconds,false);
  }
  
  public static void sleepForAndShouldInterrupt(long milliseconds) {
    sleepForAndShouldInterrupt(milliseconds, true);
  }
  
  public static void sleepForAndShouldInterrupt(long milliseconds,boolean interruptCurrentThread) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      if(interruptCurrentThread) {
        Thread.currentThread().interrupt();
      }
      logger.error(e.getMessage(),e);
    }
  }
  
  public static void sleepOrSignalInterrupted(long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      Thread.interrupted();
    }
  }

}
