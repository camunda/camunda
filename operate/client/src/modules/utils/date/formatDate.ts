/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format, parseISO} from 'date-fns';

function parseDate(dateString: string | Date) {
  return typeof dateString === 'string' ? parseISO(dateString) : dateString;
}

function formatDate(
  dateString: string | Date | null,
  placeholder: string | null = '--',
) {
  return dateString
    ? format(parseDate(dateString), 'yyyy-MM-dd HH:mm:ss')
    : placeholder;
}

export {parseDate, formatDate};
