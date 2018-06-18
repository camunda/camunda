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
package io.zeebe.msgpack.el;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import java.util.ArrayList;
import java.util.List;

public class JsonConditionValidator {

  public static String validate(JsonCondition condition) {
    final List<String> errors = new ArrayList<>();

    validateCondition(condition, errors);

    if (errors.isEmpty()) {
      return null;
    } else {
      return formatErrorMessage(errors);
    }
  }

  private static void validateCondition(JsonCondition condition, final List<String> errors) {
    JsonConditionWalker.walk(
        condition,
        object -> {
          if (object instanceof JsonPath) {
            final JsonPath path = (JsonPath) object;
            final JsonPathQuery query = path.query();

            if (!query.isValid()) {
              errors.add(query.getErrorReason());
            }
          }
        });
  }

  private static String formatErrorMessage(final List<String> errors) {
    final StringBuilder builder = new StringBuilder();

    builder.append(errors.get(0));

    for (int i = 1; i < errors.size(); i++) {
      final String error = errors.get(i);

      builder.append("\n");
      builder.append(error);
    }

    return builder.toString();
  }
}
