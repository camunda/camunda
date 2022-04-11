/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.view;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Objects;

@Data
@AllArgsConstructor
public class StringViewPropertyDto implements TypedViewPropertyDto {

  private final String id;

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StringViewPropertyDto)) {
      return false;
    }
    StringViewPropertyDto stringViewPropertyDto = (StringViewPropertyDto) o;
    return Objects.equals(getId(), stringViewPropertyDto.getId());
  }

  @Override
  public String toString() {
    return getId();
  }
}
