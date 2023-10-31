/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.usertask;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.importing.IdentityLinkLogEntryDto;

import java.util.List;

public interface IdentityLinkLogWriter extends AbstractUserTaskWriter {

   List<ImportRequestDto> generateIdentityLinkLogImports(final List<IdentityLinkLogEntryDto> identityLinkLogs);

}
