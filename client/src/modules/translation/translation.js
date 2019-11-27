/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';
import moment from 'moment';
import 'moment/locale/de';

let translationObject = {};
export async function init() {
  const localeCode = getLanguage();
  const response = await get(`api/localization`, {localeCode});
  translationObject = await response.json();
  moment.locale(localeCode);
  document.documentElement.lang = localeCode;
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
