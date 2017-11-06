package org.camunda.optimize.service.util;

import java.util.UUID;

public class IdGenerator {

  public static String getNextId() {
    UUID randomUUID = UUID.randomUUID();
    return randomUUID.toString();
  }
}
