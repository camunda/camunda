/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.zeebe.process;

import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class ZeebeProcessInstanceRecordDto
    extends ZeebeRecordDto<ZeebeProcessInstanceDataDto, ProcessInstanceIntent> {}
