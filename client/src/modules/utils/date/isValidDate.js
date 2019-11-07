/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isValid, parseISO} from 'date-fns';

export function isValidDate(dateString) {
  if (typeof dateString !== 'string') {
    throw new TypeError('please provide date as string');
  }

  return isValid(parseISO(dateString));
}
