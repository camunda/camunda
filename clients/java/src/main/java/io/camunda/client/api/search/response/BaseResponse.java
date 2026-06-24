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
package io.camunda.client.api.search.response;

import io.camunda.client.api.command.ClientException;
import java.util.List;

public interface BaseResponse<T> {

  /** Returns the list of items */
  List<T> items();

  /**
   * Returns the single item or null if the item list is empty
   *
   * @throws ClientException if the items contain more than one entry
   * @return the single item or null if the item list is empty
   */
  default T singleItem() {
    final List<T> items = items();
    if (items.isEmpty()) {
      return null;
    }
    if (items.size() > 1) {
      throw new ClientException("Expecting only one item but got " + items.size());
    }
    return items.get(0);
  }
}
