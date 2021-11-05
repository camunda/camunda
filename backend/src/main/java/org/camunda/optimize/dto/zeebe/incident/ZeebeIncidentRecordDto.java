/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.zeebe.incident;

import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import lombok.EqualsAndHashCode;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;

@EqualsAndHashCode(callSuper = true)
public class ZeebeIncidentRecordDto extends ZeebeRecordDto<ZeebeIncidentDataDto, IncidentIntent> {
}
