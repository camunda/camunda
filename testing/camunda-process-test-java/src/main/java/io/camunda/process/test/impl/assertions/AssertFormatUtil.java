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
package io.camunda.process.test.impl.assertions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AssertFormatUtil {

  public static String formatNames(final String[] names) {
    return formatNames(Arrays.asList(names));
  }

  public static String formatNames(final List<String> names) {
    return names.stream()
        .map(elementName -> String.format("'%s'", elementName))
        .collect(Collectors.joining(", ", "[", "]"));
  }
}
