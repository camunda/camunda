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
import {logger} from 'common/utils/logger';
import {t} from 'i18next';
import {getCurrentDateLocale} from 'common/i18n';

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
    keySuffix += '_time';
  }

  if (isSpeech) {
    keySuffix += '_speech';
  }

  return `relativeDate${key.charAt(0).toUpperCase() + key.slice(1)}${keySuffix}`;
};

const getConfiguredTranslatedFormat = (
  key: TextMapKeys,
  isSpeech: boolean,
  includeTime: boolean,
  options = {},
) => {
  let value = '';

  if (isSpeech && includeTime) {
    value = t(getConfiguredDateKey(key, isSpeech, includeTime), {
      ...options,
      defaultValue: '',
    });
  }

  if (!value && includeTime) {
    value = t(getConfiguredDateKey(key, false, includeTime), {
      ...options,
      defaultValue: '',
    });
  }

  if (!value && isSpeech) {
    value = t(getConfiguredDateKey(key, isSpeech, false), {
      ...options,
      defaultValue: '',
    });
  }

  return (
    value ||
    t(getConfiguredDateKey(key, false, false), {
      ...options,
      defaultValue: '',
    })
  );
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

function getFormat({
  resolution,
  isSpeech = false,
  includeTime = false,
}: {
  resolution: Resolution;
  isSpeech?: boolean;
  includeTime?: boolean;
}) {
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
  const text = format(time, getFormat({resolution}), {
    locale: getCurrentDateLocale(),
  });
  const speech = format(time, getFormat({resolution, isSpeech: true}), {
    locale: getCurrentDateLocale(),
  });
  const absoluteText = format(time, getFormat({resolution: {type: 'years'}}), {
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
  const text = format(time, getFormat({resolution, includeTime: true}), {
    locale: getCurrentDateLocale(),
  });
  const speech = format(
    time,
    getFormat({resolution, isSpeech: true, includeTime: true}),
    {
      locale: getCurrentDateLocale(),
    },
  );
  const absoluteText = format(
    time,
    getFormat({resolution: {type: 'years'}, includeTime: true}),
    {
      locale: getCurrentDateLocale(),
    },
  );
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
