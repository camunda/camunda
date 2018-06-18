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

public class JsonConditionWalker {

  public static void walk(JsonCondition condition, JsonConditionVisitor visitor) {
    if (condition instanceof Comparison) {
      visitComparison((Comparison) condition, visitor);
    } else if (condition instanceof Operator) {
      final Operator operator = (Operator) condition;

      walk(operator.x(), visitor);
      walk(operator.y(), visitor);
    } else {
      throw new RuntimeException(String.format("Illegal condition: %s", condition));
    }
  }

  private static void visitComparison(Comparison comparison, JsonConditionVisitor visitor) {
    visitor.visitObject(comparison.x());
    visitor.visitObject(comparison.y());
  }
}
