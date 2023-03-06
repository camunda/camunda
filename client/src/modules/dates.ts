/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {format as formatFns, Locale} from 'date-fns';

export let globalLocale: Locale;

// by providing a default string of 'PP' or any of its variants for `formatStr`
// it will format dates in whichever way is appropriate to the locale
export function format(date: Date, formatStr = 'PP'): string {
  return formatFns(date, formatStr, {
    locale: globalLocale,
  });
}

export async function loadDateTranslation(localeCode: string): Promise<void> {
  switch (localeCode) {
    case 'de':
      globalLocale = (await import('date-fns/locale/de/index.js')).default;
      break;
    case 'fr':
      globalLocale = (await import('date-fns/locale/fr/index.js')).default;
      break;
    case 'es':
      globalLocale = (await import('date-fns/locale/es/index.js')).default;
      break;
    case 'hi':
      globalLocale = (await import('date-fns/locale/hi/index.js')).default;
      break;
    case 'it':
      globalLocale = (await import('date-fns/locale/it/index.js')).default;
      break;
    case 'ja':
      globalLocale = (await import('date-fns/locale/ja/index.js')).default;
      break;
    case 'pt':
      globalLocale = (await import('date-fns/locale/pt/index.js')).default;
      break;
    case 'ru':
      globalLocale = (await import('date-fns/locale/ru/index.js')).default;
      break;
    default:
      break;
  }
}

export const BACKEND_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxx";
