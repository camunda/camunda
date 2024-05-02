/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
