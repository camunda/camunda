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
package io.camunda.zeebe.client.api.search.filter.builder;

import java.util.function.UnaryOperator;

public class PropertyBuilderCallbacks {

  @FunctionalInterface
  public interface StringPropertyBuilderCallback extends UnaryOperator<StringPropertyBuilder> {}

  @FunctionalInterface
  public interface BasicLongPropertyBuilderCallback
      extends UnaryOperator<BasicLongPropertyBuilder> {}

  @FunctionalInterface
  public interface LongPropertyBuilderCallback extends UnaryOperator<LongPropertyBuilder> {}

  @FunctionalInterface
  public interface IntegerPropertyBuilderCallback extends UnaryOperator<IntegerPropertyBuilder> {}

  @FunctionalInterface
  public interface DateTimePropertyBuilderCallback extends UnaryOperator<DateTimePropertyBuilder> {}

  @FunctionalInterface
  public interface ProcessInstanceStatePropertyBuilderCallback
      extends UnaryOperator<ProcessInstanceStatePropertyBuilder> {}
}
