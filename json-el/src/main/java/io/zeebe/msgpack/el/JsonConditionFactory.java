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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import scala.util.parsing.combinator.Parsers.ParseResult;

public class JsonConditionFactory {

  public static CompiledJsonCondition createCondition(String expression) {
    if (expression == null || expression.isEmpty()) {
      return CompiledJsonCondition.fail(expression, "expression is empty");
    }

    final ParseResult<JsonCondition> result = JsonConditionParser.parse(expression);

    if (result.successful()) {
      final JsonCondition condition = result.get();

      final String errorMessage = JsonConditionValidator.validate(condition);

      if (errorMessage == null) {
        // index is used by the interpreter for caching
        indexJsonPathExpressions(condition);

        return CompiledJsonCondition.success(expression, condition);
      } else {
        return CompiledJsonCondition.fail(expression, errorMessage);
      }
    } else {
      return CompiledJsonCondition.fail(expression, result.toString());
    }
  }

  private static void indexJsonPathExpressions(JsonCondition condition) {
    final List<JsonPath> pathExpressions = new ArrayList<>();

    JsonConditionWalker.walk(
        condition,
        object -> {
          if (object instanceof JsonPath) {
            pathExpressions.add((JsonPath) object);
          }
        });

    final AtomicInteger nextId = new AtomicInteger(1);

    pathExpressions.stream()
        .collect(Collectors.groupingBy(JsonPath::jsonPath))
        .values()
        .stream()
        .filter(l -> l.size() > 1)
        .forEach(
            l -> {
              final int id = nextId.getAndIncrement();

              l.forEach(p -> p.id(id));
            });
  }
}
