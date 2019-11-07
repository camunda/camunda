/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {format, parseISO} from 'date-fns';

function parseDate(dateString) {
  return typeof dateString === 'string' ? parseISO(dateString) : dateString;
}

export function formatDate(dateString) {
  return dateString
    ? format(parseDate(dateString), 'yyyy-MM-dd HH:mm:ss')
    : '--';
}
