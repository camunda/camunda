/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_TAGS_COUNT;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_TAG_FORMAT;

import io.camunda.zeebe.util.TagUtil;
import java.util.List;
import java.util.Set;

public class TagsValidator {

  public static void validate(final Set<String> tags, final List<String> violations) {
    if (tags == null || tags.isEmpty()) {
      return;
    }

    if (tags.size() > TagUtil.MAX_NUMBER_OF_TAGS) {
      violations.add(
          ERROR_MESSAGE_INVALID_TAGS_COUNT.formatted(tags.size(), TagUtil.MAX_NUMBER_OF_TAGS));
    }

    for (final String tag : tags) {
      if (!TagUtil.isValidTag(tag)) {
        violations.add(
            ERROR_MESSAGE_INVALID_TAG_FORMAT.formatted(tag, TagUtil.TAG_FORMAT_DESCRIPTION));
      }
    }
  }
}
