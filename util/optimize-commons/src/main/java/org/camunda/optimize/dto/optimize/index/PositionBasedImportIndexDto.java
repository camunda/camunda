/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.index;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class PositionBasedImportIndexDto extends ImportIndexDto<ZeebeDataSourceDto> {

  protected long positionOfLastEntity = 0;
  protected long sequenceOfLastEntity = 0;
  protected String esTypeIndexRefersTo;
  protected boolean hasSeenSequenceField = false; // flag to indicate whether at least one record with a sequence field has been imported

}
