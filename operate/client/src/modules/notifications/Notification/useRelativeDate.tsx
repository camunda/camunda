/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  format,
  parseISO,
  differenceInHours,
  differenceInSeconds,
  formatDistanceToNowStrict,
} from 'date-fns';
import {useEffect, useState} from 'react';
import {logger} from 'modules/logger';

const formatDateTime = (dateString: string) => {
  try {
    return format(parseISO(dateString), 'dd MMM yyyy - hh:mm a');
  } catch (error) {
    logger.error(error);
    return '';
  }
};

function getRelativeDate(date: number): string {
  if (differenceInSeconds(Date.now(), date) <= 10) {
    return 'Just now';
  }

  if (differenceInHours(date, Date.now()) > 0) {
    return formatDateTime(new Date(date).toISOString());
  }

  return formatDistanceToNowStrict(date);
}

function useRelativeDate(targetDate: number): string {
  const [relativeDate, setRelativeDate] = useState(getRelativeDate(targetDate));

  useEffect(() => {
    const intervalID = setInterval(() => {
      setRelativeDate(getRelativeDate(targetDate));
    }, 1000);

    return () => {
      clearInterval(intervalID);
    };
  }, [targetDate]);

  return relativeDate;
}

export {useRelativeDate};
