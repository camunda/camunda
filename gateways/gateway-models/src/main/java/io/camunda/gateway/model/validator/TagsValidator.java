/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.model.validator;

import static io.camunda.gateway.model.validator.ErrorMessages.ERROR_MESSAGE_INVALID_TAGS_COUNT;
import static io.camunda.gateway.model.validator.ErrorMessages.ERROR_MESSAGE_INVALID_TAG_FORMAT;

import io.camunda.zeebe.util.TagUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TagsValidator {

  public static List<String> validate(final Set<String> tags) {
    final List<String> errors = new ArrayList<>();
    if (tags == null || tags.isEmpty()) {
      return errors;
    }

    if (tags.size() > TagUtil.MAX_NUMBER_OF_TAGS) {
      errors.add(
          ERROR_MESSAGE_INVALID_TAGS_COUNT.formatted(tags.size(), TagUtil.MAX_NUMBER_OF_TAGS));
    }

    for (final String tag : tags) {
      if (!TagUtil.isValidTag(tag)) {
        errors.add(ERROR_MESSAGE_INVALID_TAG_FORMAT.formatted(tag, TagUtil.TAG_FORMAT_DESCRIPTION));
      }
    }
    return errors;
  }
}
