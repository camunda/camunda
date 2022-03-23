/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.sequence;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.query.event.process.EventDto;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class OrderedEventDto extends EventDto {

  private Long orderCounter;

}
