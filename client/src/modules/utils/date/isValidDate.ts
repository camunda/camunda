/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isValid, parseISO} from 'date-fns';

export function isValidDate(dateString: any) {
  if (typeof dateString !== 'string') {
    throw new TypeError('please provide date as string');
  }

  return isValid(parseISO(dateString));
}
