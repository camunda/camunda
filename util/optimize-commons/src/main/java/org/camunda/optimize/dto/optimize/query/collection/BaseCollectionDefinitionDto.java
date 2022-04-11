/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;

@Data
@FieldNameConstants(asEnum = true)
public class BaseCollectionDefinitionDto<DATA_TYPE> {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected DATA_TYPE data;

}
