/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  addDays,
  differenceInMinutes,
  format,
  isSameDay,
  isSameWeek,
  isSameYear,
  parseISO,
  subDays,
} from 'date-fns';
import {logger} from './logger';
import {t} from 'i18next';
import {getCurrentDateLocale} from 'modules/internationalization';

type TextMapKeys =
  | 'now'
  | 'inOneMinute'
  | 'inXMinutes'
  | 'oneMinuteAgo'
  | 'xMinutesAgo'
  | 'today'
  | 'yesterday'
  | 'tomorrow'
  | 'weekFmt'
  | 'monthFmt'
  | 'yearsFmt'
  | 'unknown';

type Resolution =
  | {
      type: 'now';
    }
  | {
      type: 'minutes' | 'days';
      amount: number;
    }
  | {
      type: 'week' | 'months' | 'years';
    };

const getConfiguredDateKey = (
  key: TextMapKeys,
  isSpeech: boolean,
  includeTime: boolean,
) => {
  let keySuffix = '';

  if (includeTime) {
    keySuffix += '_withTime';
  }

  if (isSpeech) {
    keySuffix += '_speech';
  }

  return `relativeDateFormat_${key}${keySuffix}`;
};

const getConfiguredTranslatedFormat = (
  key: TextMapKeys,
  isSpeech: boolean,
  includeTime: boolean,
  options = {},
) => {
  let value = '';

  // Allows inheritance of the format if the time or speech equivalent is not defined
  if (isSpeech && includeTime) {
    value = t(getConfiguredDateKey(key, isSpeech, includeTime), {
      ...options,
      defaultValue: '',
    });
  }
  if (includeTime) {
    value =
      value ||
      t(getConfiguredDateKey(key, false, includeTime), {
        ...options,
        defaultValue: '',
      });
  }
  if (isSpeech) {
    value =
      value ||
      t(getConfiguredDateKey(key, isSpeech, false), {
        ...options,
        defaultValue: '',
      });
  }
  value =
    value ||
    t(getConfiguredDateKey(key, false, false), {...options, defaultValue: ''});

  if (typeof value !== 'string') {
    console.warn(`Unexpected translation type for 'relativeDateFormat_${key}'`);
  }

  return value;
};

function getResolution(time: Date, now: Date): Resolution {
  const diffInMinutes = differenceInMinutes(time, now);
  if (diffInMinutes === 0) {
    return {type: 'now'};
  }
  if (diffInMinutes >= -60 && diffInMinutes <= 60) {
    return {
      type: 'minutes',
      amount: diffInMinutes,
    };
  }
  if (isSameDay(time, now)) {
    return {type: 'days', amount: 0};
  }
  if (isSameDay(time, addDays(now, 1))) {
    return {type: 'days', amount: 1};
  }
  if (isSameDay(time, subDays(now, 1))) {
    return {type: 'days', amount: -1};
  }
  if (isSameWeek(time, now)) {
    return {type: 'week'};
  }
  if (isSameYear(time, now)) {
    return {type: 'months'};
  }
  return {type: 'years'};
}

function getFormat(
  resolution: Resolution,
  isSpeech: boolean = false,
  includeTime: boolean = false,
) {
  const getTranslatedFormat = (key: TextMapKeys, options = {}) =>
    getConfiguredTranslatedFormat(key, isSpeech, includeTime, options);

  switch (resolution.type) {
    case 'now':
      return getTranslatedFormat('now');
    case 'minutes':
      if (resolution.amount === 1) {
        return getTranslatedFormat('inOneMinute');
      }
      if (resolution.amount > 1) {
        return getTranslatedFormat('inXMinutes', {amount: resolution.amount});
      }
      if (resolution.amount === -1) {
        return getTranslatedFormat('oneMinuteAgo');
      }
      if (resolution.amount < -1) {
        return getTranslatedFormat('xMinutesAgo', {amount: -resolution.amount});
      }
      return getTranslatedFormat('unknown');
    case 'days':
      if (resolution.amount === 0) {
        return getTranslatedFormat('today');
      }
      if (resolution.amount === 1) {
        return getTranslatedFormat('tomorrow');
      }
      if (resolution.amount === -1) {
        return getTranslatedFormat('yesterday');
      }
      return '';
    case 'week':
      return getTranslatedFormat('weekFmt');
    case 'months':
      return getTranslatedFormat('monthFmt');
    case 'years':
      return getTranslatedFormat('yearsFmt');
    default:
      return '';
  }
}

function formatDate(time: Date, now?: Date) {
  const resolution = getResolution(time, now ?? new Date());
  const text = format(time, getFormat(resolution), {
    locale: getCurrentDateLocale(),
  });
  const speech = format(time, getFormat(resolution, true), {
    locale: getCurrentDateLocale(),
  });
  const absoluteText = format(time, getFormat({type: 'years'}), {
    locale: getCurrentDateLocale(),
  });
  return {
    date: time,
    relative: {
      resolution: resolution.type,
      text,
      speech,
    },
    absolute: {
      text: absoluteText,
    },
  };
}

function formatISODate(dateString: string | null) {
  if (dateString === null) {
    return null;
  }
  try {
    return formatDate(parseISO(dateString));
  } catch (error) {
    logger.error(error);
    return null;
  }
}

function formatDateTime(time: Date, now?: Date) {
  const resolution = getResolution(time, now ?? new Date());
  const text = format(time, getFormat(resolution, false, true), {
    locale: getCurrentDateLocale(),
  });
  const speech = format(time, getFormat(resolution, true, true), {
    locale: getCurrentDateLocale(),
  });
  const absoluteText = format(time, getFormat({type: 'years'}, false, true), {
    locale: getCurrentDateLocale(),
  });
  return {
    date: time,
    relative: {
      resolution: resolution.type,
      text,
      speech,
    },
    absolute: {
      text: absoluteText,
    },
  };
}

function formatISODateTime(dateString: string | null) {
  if (dateString === null) {
    return null;
  }
  try {
    return formatDateTime(parseISO(dateString));
  } catch (error) {
    logger.error(error);
    return null;
  }
}

export {formatDate, formatISODate, formatDateTime, formatISODateTime};
