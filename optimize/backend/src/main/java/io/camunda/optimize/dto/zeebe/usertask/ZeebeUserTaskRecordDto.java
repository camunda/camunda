/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.dto.zeebe.usertask;

import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;

public class ZeebeUserTaskRecordDto extends ZeebeRecordDto<ZeebeUserTaskDataDto, UserTaskIntent> {}
