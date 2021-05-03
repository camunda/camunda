/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

public final class UnsupportedBrokerResponseException extends BrokerResponseException {

  private static final String SBE_SCHEMA_TEMPLATE_FORMAT =
      "Expected to receive message with schema id '%d' and template id '%d', but received schema id '%d' and template id '%d'";
  private static final String VALUE_TYPE_FORMAT =
      "Expected command response with value type '%s', but received '%s'";

  public UnsupportedBrokerResponseException(
      final int expectedSchemaId,
      final int expectedTemplateId,
      final int actualSchemaId,
      final int actualTemplateId) {
    super(
        String.format(
            SBE_SCHEMA_TEMPLATE_FORMAT,
            expectedSchemaId,
            expectedTemplateId,
            actualSchemaId,
            actualTemplateId));
  }

  public UnsupportedBrokerResponseException(
      final String expectedValueType, final String actualValueType) {
    super(String.format(VALUE_TYPE_FORMAT, expectedValueType, actualValueType));
  }
}
