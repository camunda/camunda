/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isValid, parseISO} from 'date-fns';
import {format} from 'dates';

export const DATE_FORMAT = 'yyyy-MM-dd';

export function isDateValid(date: string) {
  const parsedDate = parseISO(date);
  return isValid(parsedDate) && format(parsedDate, DATE_FORMAT) === date;
}
