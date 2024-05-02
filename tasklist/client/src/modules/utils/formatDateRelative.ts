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
import {enUS} from 'date-fns/locale';
import {logger} from './logger';

type TextMap = {
  NOW: string;
  IN_ONE_MINUTE: string;
  IN_X_MINUTES: string;
  ONE_MINUTE_AGO: string;
  X_MINUTES_AGO: string;
  TODAY: string;
  YESTERDAY: string;
  TOMORROW: string;
  WEEK_FMT: string;
  MONTH_FMT: string;
  YEARS_FMT: string;
};

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

const TEXT_DATE_LANG_EN: TextMap = {
  NOW: "'Now'",
  IN_ONE_MINUTE: "'In 1 minute'",
  IN_X_MINUTES: "'In {amount} minutes'",
  ONE_MINUTE_AGO: "'1 minute ago'",
  X_MINUTES_AGO: "'{amount} minutes ago'",
  TODAY: "'Today'",
  YESTERDAY: "'Yesterday'",
  TOMORROW: "'Tomorrow'",
  WEEK_FMT: 'EEEE',
  MONTH_FMT: 'd MMM',
  YEARS_FMT: 'd MMM yyyy',
};

const SPEECH_DATE_LANG_EN: TextMap = {
  ...TEXT_DATE_LANG_EN,
  MONTH_FMT: "do 'of' MMMM",
  YEARS_FMT: "do 'of' MMMM, yyyy",
};

const TEXT_DATETIME_LANG_EN: TextMap = {
  ...TEXT_DATE_LANG_EN,
  TODAY: TEXT_DATE_LANG_EN['TODAY'] + ', HH:mm',
  YESTERDAY: TEXT_DATE_LANG_EN['YESTERDAY'] + ', HH:mm',
  TOMORROW: TEXT_DATE_LANG_EN['TOMORROW'] + ', HH:mm',
  WEEK_FMT: 'EEEE, HH:mm',
  MONTH_FMT: 'd MMM, HH:mm',
  YEARS_FMT: 'd MMM yyyy, HH:mm',
};

const SPEECH_DATETIME_LANG_EN: TextMap = {
  ...TEXT_DATE_LANG_EN,
  TODAY: TEXT_DATE_LANG_EN['TODAY'] + " 'at' HH:mm",
  YESTERDAY: TEXT_DATE_LANG_EN['YESTERDAY'] + " 'at' HH:mm",
  TOMORROW: TEXT_DATE_LANG_EN['TOMORROW'] + " 'at' HH:mm",
  WEEK_FMT: "EEEE 'at' HH:mm",
  MONTH_FMT: "do 'of' MMMM 'at' HH:mm",
  YEARS_FMT: "do 'of' MMMM, yyyy 'at' HH:mm",
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

function getFormat(resolution: Resolution, lang: TextMap) {
  switch (resolution.type) {
    case 'now':
      return lang['NOW'];
    case 'minutes':
      if (resolution.amount === 1) {
        return lang['IN_ONE_MINUTE'];
      }
      if (resolution.amount > 1) {
        const amount = resolution.amount.toString();
        return lang['IN_X_MINUTES'].replace('{amount}', amount);
      }
      if (resolution.amount === -1) {
        return lang['ONE_MINUTE_AGO'];
      }
      if (resolution.amount < -1) {
        const amount = (-resolution.amount).toString();
        return lang['X_MINUTES_AGO'].replace('{amount}', amount);
      }
      return "'Unknown'";
    case 'days':
      if (resolution.amount === 0) {
        return lang['TODAY'];
      }
      if (resolution.amount === 1) {
        return lang['TOMORROW'];
      }
      if (resolution.amount === -1) {
        return lang['YESTERDAY'];
      }
      return '';
    case 'week':
      return lang['WEEK_FMT'];
    case 'months':
      return lang['MONTH_FMT'];
    case 'years':
      return lang['YEARS_FMT'];
    default:
      return '';
  }
}

function formatDate(time: Date, now?: Date) {
  const resolution = getResolution(time, now ?? new Date());
  const text = format(time, getFormat(resolution, TEXT_DATE_LANG_EN), {
    locale: enUS,
  });
  const speech = format(time, getFormat(resolution, SPEECH_DATE_LANG_EN), {
    locale: enUS,
  });
  const absoluteText = format(
    time,
    getFormat({type: 'years'}, TEXT_DATE_LANG_EN),
    {
      locale: enUS,
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
  const text = format(time, getFormat(resolution, TEXT_DATETIME_LANG_EN), {
    locale: enUS,
  });
  const speech = format(time, getFormat(resolution, SPEECH_DATETIME_LANG_EN), {
    locale: enUS,
  });
  const absoluteText = format(
    time,
    getFormat({type: 'years'}, TEXT_DATETIME_LANG_EN),
    {
      locale: enUS,
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
