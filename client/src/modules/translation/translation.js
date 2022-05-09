/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get} from 'request';
import {getOptimizeVersion} from 'config';
import {loadDateTranslation} from 'dates';

let translationObject = {};
export async function initTranslation() {
  const localeCode = getLanguage();
  await loadDateTranslation(localeCode);
  const response = await get(`api/localization`, {version: await getOptimizeVersion(), localeCode});
  translationObject = await response.json();
}

export function t(key, data) {
  return injectData(findValue(key, translationObject), data);
}

export function getLanguage() {
  const nav = window.navigator;
  const browserLang = (
    (Array.isArray(nav.languages)
      ? nav.languages[0]
      : nav.language || nav.browserLanguage || nav.systemLanguage || nav.userLanguage) || ''
  ).split('-');

  return browserLang[0].toLowerCase();
}

function injectData(template, data) {
  return template.replace(/\{([\w.]*)\}/g, (str, key) => findValue(key, data));
}

function findValue(key, data = {}) {
  const keys = key.split('.');
  let v = data;
  for (let i = 0; i < keys.length; i++) {
    v = v[keys[i]];
    if (typeof v === 'undefined') {
      throw new Error(`"${keys[i]}" key of "${key}" not found in translation object`);
    }
  }
  return v;
}
