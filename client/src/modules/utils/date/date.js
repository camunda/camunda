/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {format} from 'date-fns';

export function formatDate(dateString) {
  return dateString ? format(dateString, 'YYYY-MM-DD HH:mm:ss') : '--';
}
