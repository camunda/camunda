/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  differenceInSeconds,
  formatDistanceStrict,
  formatDistanceToNowStrict,
  parseISO,
} from 'date-fns';

function getExecutionDuration(
  startDate: string,
  endDate: string | null
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
