/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {format} from 'date-fns';

const formatDate = (date: Date) => {
  return format(date, 'yyyy-MM-dd');
};

const formatTime = (date: Date) => {
  return format(date, 'HH:mm:ss');
};

const formatISODate = (date: Date) => {
  return format(date, "yyyy-MM-dd'T'HH:mm:ss.SSSxx");
};

export {formatDate, formatISODate, formatTime};
