/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

let translationObject = {};
export async function init() {
  const lang = getLanguage();
  const response = await get(`api/localization?localeCode=${lang}`);
  translationObject = await response.json();
}

export function t(key, data) {
  return injectData(findValue(key, translationObject), data);
}

function getLanguage() {
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

function findValue(key, data) {
  const keys = key.split('.');
  let v = data[keys.shift()];
  for (let i = 0, l = keys.length; i < l; i++) {
    v = v[keys[i]];
  }
  return typeof v !== 'undefined' && v !== null ? v : '';
}
