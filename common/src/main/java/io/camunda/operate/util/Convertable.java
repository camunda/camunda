/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import java.util.Optional;

/*
* Convertable is an Object wrapper which allows for safe type casting
*/

public class Convertable {
  Object value;

  public Convertable(Object value) {
    this.value = value;
  }

  public static Convertable from(Object value) {
    return new Convertable(value);
  }

  public <R> Optional<R> to() {
    try {
      return Optional.ofNullable((R) value);
    } catch (ClassCastException e) {
      return Optional.empty();
    }
  }

}
