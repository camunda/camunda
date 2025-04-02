/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatISODate} from 'common/dates/formatDateRelative';
import {isBefore} from 'date-fns';

function getSecondaryDate({
  completionDate,
  dueDate,
  followUpDate,
  sortBy,
}: {
  completionDate: ReturnType<typeof formatISODate>;
  dueDate: ReturnType<typeof formatISODate>;
  followUpDate: ReturnType<typeof formatISODate>;
  sortBy: 'creation' | 'follow-up' | 'due' | 'completion' | 'priority';
}) {
  const now = Date.now();
  const isOverdue = !completionDate && dueDate && isBefore(dueDate.date, now);
  const isFollowupBeforeDueDate =
    dueDate && followUpDate && isBefore(followUpDate.date, dueDate.date);

  if (sortBy === 'creation' && completionDate) {
    return {completionDate};
  } else if (
    sortBy === 'creation' &&
    followUpDate &&
    !isOverdue &&
    isFollowupBeforeDueDate
  ) {
    return {followUpDate};
  } else if (sortBy === 'completion' && completionDate) {
    return {completionDate};
  } else if (sortBy === 'follow-up' && followUpDate) {
    return {followUpDate};
  } else if (dueDate) {
    return isOverdue ? {overDueDate: dueDate} : {dueDate};
  } else {
    return {};
  }
}

export {getSecondaryDate};
