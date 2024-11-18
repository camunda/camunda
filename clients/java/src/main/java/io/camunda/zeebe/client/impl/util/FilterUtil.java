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
package io.camunda.zeebe.client.impl.util;

import io.camunda.zeebe.client.protocol.rest.BasicLongFilterProperty;
import io.camunda.zeebe.client.protocol.rest.IntegerFilterProperty;
import io.camunda.zeebe.client.protocol.rest.LongFilterProperty;
import io.camunda.zeebe.client.protocol.rest.StringFilterProperty;

public class FilterUtil {

  public static BasicLongFilterProperty basicLongFilterProperty(final Long value) {
    final BasicLongFilterProperty filter = new BasicLongFilterProperty();
    filter.$eq(value);
    return filter;
  }

  public static LongFilterProperty longFilterProperty(final Long value) {
    final LongFilterProperty filter = new LongFilterProperty();
    filter.$eq(value);
    return filter;
  }

  public static IntegerFilterProperty integerFilterProperty(final Integer value) {
    final IntegerFilterProperty filter = new IntegerFilterProperty();
    filter.$eq(value);
    return filter;
  }

  public static StringFilterProperty stringFilterProperty(final String value) {
    final StringFilterProperty filter = new StringFilterProperty();
    filter.$eq(value);
    return filter;
  }
}
