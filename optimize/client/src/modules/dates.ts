/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {format as formatFns, Locale} from 'date-fns';

export let globalLocale: Locale;

// by providing a default string of 'PP' or any of its variants for `formatStr`
// it will format dates in whichever way is appropriate to the locale
export function format(date: Date | number, formatStr = 'PP'): string {
  return formatFns(date, formatStr, {
    locale: globalLocale,
  });
}

export async function loadDateTranslation(localeCode: string): Promise<void> {
  switch (localeCode) {
    case 'de':
      globalLocale = (await import('date-fns/locale/de')).default;
      break;
    case 'fr':
      globalLocale = (await import('date-fns/locale/fr')).default;
      break;
    case 'es':
      globalLocale = (await import('date-fns/locale/es')).default;
      break;
    case 'hi':
      globalLocale = (await import('date-fns/locale/hi')).default;
      break;
    case 'it':
      globalLocale = (await import('date-fns/locale/it')).default;
      break;
    case 'ja':
      globalLocale = (await import('date-fns/locale/ja')).default;
      break;
    case 'pt':
      globalLocale = (await import('date-fns/locale/pt')).default;
      break;
    case 'ru':
      globalLocale = (await import('date-fns/locale/ru')).default;
      break;
    default:
      break;
  }
}

export const BACKEND_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxx";
