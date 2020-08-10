/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
  if (localeCode !== 'en') {
    globalLocale = await import(`date-fns/locale/${localeCode}/index.js`);
  }
}
