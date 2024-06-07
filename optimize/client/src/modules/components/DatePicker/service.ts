/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isValid, parseISO} from 'date-fns';
import {format} from 'dates';

export const DATE_FORMAT = 'yyyy-MM-dd';

export function isDateValid(date: string) {
  const parsedDate = parseISO(date);
  return isValid(parsedDate) && format(parsedDate, DATE_FORMAT) === date;
}
