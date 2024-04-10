/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
