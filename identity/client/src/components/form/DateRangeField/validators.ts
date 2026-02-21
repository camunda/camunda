/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { isValid, parse, parseISO } from "date-fns";
import { FormValues } from "src/components/form/DateRangeField/DateRangeModal/TimeInput";

const VALIDATION_TIMEOUT = 750;
const TIME_ERROR = "Time has to be in the format hh:mm:ss";
const TIME_RANGE_ERROR = '"From time" is after "To time"';

function parseDate(dateString: string | Date) {
  return typeof dateString === "string" ? parseISO(dateString) : dateString;
}

function parseFilterTime(value: string) {
  const HOUR_MINUTES_PATTERN = /^[0-9]{2}:[0-9]{2}$/;
  const HOUR_MINUTES_SECONDS_PATTERN = /^[0-9]{2}:[0-9]{2}:[0-9]{2}$/;

  if (HOUR_MINUTES_PATTERN.test(value)) {
    const parsedDate = parse(value, "HH:mm", new Date());
    return isValid(parsedDate) ? parsedDate : undefined;
  }

  if (HOUR_MINUTES_SECONDS_PATTERN.test(value)) {
    const parsedDate = parse(value, "HH:mm:ss", new Date());
    return isValid(parsedDate) ? parsedDate : undefined;
  }
}

export const validateTimeComplete = async (
  value: string | undefined,
): Promise<true | string> => {
  if (value && value !== "" && !isValid(parseFilterTime(value.trim()))) {
    await new Promise((r) => setTimeout(r, VALIDATION_TIMEOUT));
    return TIME_ERROR;
  }

  return true;
};

export const validateTimeRange = (
  _: string,
  { fromDate, fromTime, toDate, toTime }: FormValues,
): true | string => {
  if (!fromDate || !toDate || !fromTime || !toTime) {
    return true;
  }

  const parsedFromDate = parseDate(fromDate).getTime();
  const parsedToDate = parseDate(toDate).getTime();
  const parsedFromTime = parseFilterTime(fromTime.trim())?.getTime() ?? 0;
  const parsedToTime = parseFilterTime(toTime.trim())?.getTime() ?? 0;

  if (parsedFromDate === parsedToDate && parsedFromTime > parsedToTime) {
    return TIME_RANGE_ERROR;
  }

  return true;
};

export const validateTimeCharacters = (value: string): true | string => {
  if (value !== "" && value.replace(/[0-9]|:/g, "") !== "") {
    return TIME_ERROR;
  }
  return true;
};
