/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {format as formatFns} from 'date-fns';
export let globalLocale;

// by providing a default string of 'PP' or any of its variants for `formatStr`
// it will format dates in whichever way is appropriate to the locale
export function format(date, formatStr = 'PP') {
  return formatFns(date, formatStr, {
    locale: globalLocale,
  });
}

export async function loadDateTranslation(localeCode) {
  switch (localeCode) {
    case 'de':
      return (globalLocale = await import('date-fns/locale/de/index.js'));
    case 'fr':
      return (globalLocale = await import('date-fns/locale/fr/index.js'));
    case 'es':
      return (globalLocale = await import('date-fns/locale/es/index.js'));
    case 'hi':
      return (globalLocale = await import('date-fns/locale/hi/index.js'));
    case 'it':
      return (globalLocale = await import('date-fns/locale/it/index.js'));
    case 'ja':
      return (globalLocale = await import('date-fns/locale/ja/index.js'));
    case 'pt':
      return (globalLocale = await import('date-fns/locale/pt/index.js'));
    case 'ru':
      return (globalLocale = await import('date-fns/locale/ru/index.js'));
    default:
  }
}

export const BACKEND_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxx";
