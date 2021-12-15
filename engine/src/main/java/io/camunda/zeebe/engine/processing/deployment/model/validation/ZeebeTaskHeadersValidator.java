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
import java.util.Set;
import java.util.function.Predicate;
import org.agrona.Strings;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeTaskHeadersValidator implements ModelElementValidator<ZeebeTaskHeaders> {

  private static final Set<String> RESTRICTED_HEADERS =
      Set.of(
          Protocol.USER_TASK_ASSIGNEE_HEADER_NAME,
          Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
          Protocol.USER_TASK_FORM_KEY_HEADER_NAME);

  @Override
  public Class<ZeebeTaskHeaders> getElementType() {
    return ZeebeTaskHeaders.class;
  }

  @Override
  public void validate(
      final ZeebeTaskHeaders element, final ValidationResultCollector validationResultCollector) {
    element.getHeaders().stream()
        .map(ZeebeHeader::getKey)
        .filter(Predicate.not(Strings::isEmpty))
        .filter(RESTRICTED_HEADERS::contains)
        .map(key -> String.format("Attribute 'key' may not use restricted header '%s'", key))
        .forEach(message -> validationResultCollector.addError(0, message));
  }
}
