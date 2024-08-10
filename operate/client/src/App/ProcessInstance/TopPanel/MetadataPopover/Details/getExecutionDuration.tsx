/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  differenceInSeconds,
  formatDistanceStrict,
  formatDistanceToNowStrict,
  parseISO,
} from 'date-fns';

function getExecutionDuration(
  startDate: string,
  endDate: string | null,
): string {
  const parsedStartDate = parseISO(startDate);

  if (endDate === null) {
    return `${formatDistanceToNowStrict(parsedStartDate)} (running)`;
  }

  const parsedEndDate = parseISO(endDate);

  if (differenceInSeconds(parsedEndDate, parsedStartDate) > 0) {
    return formatDistanceStrict(parsedEndDate, parsedStartDate);
  }

  return 'Less than 1 second';
}

export {getExecutionDuration};
