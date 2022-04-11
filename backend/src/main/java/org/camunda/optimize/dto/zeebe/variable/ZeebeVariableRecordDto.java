/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.zeebe.variable;

import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;

@EqualsAndHashCode(callSuper = true)
public class ZeebeVariableRecordDto extends ZeebeRecordDto<ZeebeVariableDataDto, VariableIntent> {
}
