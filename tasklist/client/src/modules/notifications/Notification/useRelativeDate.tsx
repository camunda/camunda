/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  differenceInHours,
  differenceInSeconds,
  formatDistanceToNowStrict,
} from 'date-fns';
import {formatDate} from 'modules/utils/formatDate';
import {useEffect, useState} from 'react';

function getRelativeDate(date: number): string {
  if (differenceInSeconds(Date.now(), date) <= 10) {
    return 'Just now';
  }

  if (differenceInHours(date, Date.now()) > 0) {
    return formatDate(new Date(date).toISOString());
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
