/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.camunda.zeebe.protocol.Protocol;
import java.util.Objects;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeTaskHeadersValidator implements ModelElementValidator<ZeebeTaskHeaders> {

  private static final String RESERVED_HEADER_NAME_PREFIX = Protocol.RESERVED_HEADER_NAME_PREFIX;
  private static final String RESERVED_KEY_MESSAGE_TEMPLATE =
      "Attribute 'key' contains '%s', but header keys starting with '%s' are reserved for internal use.";

  @Override
  public Class<ZeebeTaskHeaders> getElementType() {
    return ZeebeTaskHeaders.class;
  }

  @Override
  public void validate(
      final ZeebeTaskHeaders element, final ValidationResultCollector validationResultCollector) {
    element.getHeaders().stream()
        .map(ZeebeHeader::getKey)
        .filter(Objects::nonNull)
        .filter(x -> x.startsWith(RESERVED_HEADER_NAME_PREFIX))
        .map(key -> String.format(RESERVED_KEY_MESSAGE_TEMPLATE, key, RESERVED_HEADER_NAME_PREFIX))
        .forEach(message -> validationResultCollector.addError(0, message));
  }
}
