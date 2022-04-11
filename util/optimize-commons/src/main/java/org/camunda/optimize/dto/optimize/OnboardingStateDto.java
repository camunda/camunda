/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@FieldNameConstants
public class OnboardingStateDto {
  private static final String ID_SEGMENT_SEPARATOR = ":";

  @NonNull
  private String id;
  private String key;
  private String userId;
  private boolean seen;

  public OnboardingStateDto(@NonNull final String key, @NonNull final String userId) {
    this(key, userId, false);
  }

  public OnboardingStateDto(@NonNull final String key, @NonNull final String userId, final boolean seen) {
    this.id = convertToId(userId, key);
    this.userId = userId;
    this.key = key;
    this.seen = seen;
  }

  private String convertToId(final String userId, final String key) {
    return userId + ID_SEGMENT_SEPARATOR + key;
  }
}
