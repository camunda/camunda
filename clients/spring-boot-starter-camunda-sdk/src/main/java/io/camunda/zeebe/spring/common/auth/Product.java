/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.spring.common.auth;

import java.util.Arrays;

/** Enum for supported C8 Products */
public enum Product {
  ZEEBE(true),
  IDENTITY(true);

  private final boolean covered;

  Product(final boolean covered) {
    this.covered = covered;
  }

  public static Product[] coveredProducts() {
    return Arrays.stream(Product.values())
        .filter(Product::covered)
        .toList()
        .toArray(new Product[0]);
  }

  public boolean covered() {
    return covered;
  }
}
