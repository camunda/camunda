/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.query.TerminatedUserSessionDto;

import java.time.OffsetDateTime;

public interface TerminatedUserSessionWriter {

  void writeTerminatedUserSession(final TerminatedUserSessionDto sessionDto);

  void deleteTerminatedUserSessionsOlderThan(final OffsetDateTime timestamp);

}
