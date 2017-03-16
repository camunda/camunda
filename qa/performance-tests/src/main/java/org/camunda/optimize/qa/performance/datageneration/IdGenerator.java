package org.camunda.optimize.qa.performance.datageneration;

import java.util.concurrent.atomic.AtomicLong;

class IdGenerator {

  private static AtomicLong idCounter = new AtomicLong();

  static String getNextId() {
    return String.valueOf(idCounter.getAndIncrement());
  }
}
