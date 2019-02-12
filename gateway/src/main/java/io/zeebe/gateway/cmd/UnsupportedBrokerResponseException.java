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
package io.zeebe.gateway.cmd;

public class UnsupportedBrokerResponseException extends BrokerResponseException {

  private static final String SBE_SCHEMA_TEMPLATE_FORMAT =
      "Expected to receive message with schema id '%d' and template id '%d', but received schema id '%d' and template id '%d'";
  private static final String VALUE_TYPE_FORMAT =
      "Expected command response with value type '%s', but received '%s'";

  public UnsupportedBrokerResponseException(
      int expectedSchemaId, int expectedTemplateId, int actualSchemaId, int actualTemplateId) {
    super(
        String.format(
            SBE_SCHEMA_TEMPLATE_FORMAT,
            expectedSchemaId,
            expectedTemplateId,
            actualSchemaId,
            actualTemplateId));
  }

  public UnsupportedBrokerResponseException(String expectedValueType, String actualValueType) {
    super(String.format(VALUE_TYPE_FORMAT, expectedValueType, actualValueType));
  }
}
