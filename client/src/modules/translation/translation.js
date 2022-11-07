/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

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
  const translatedString = injectData(findValue(key, translationObject), data);

  if (containsHTML(translatedString)) {
    return parseToJSX(translatedString);
  }

  return translatedString;
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

function containsHTML(str) {
  return str.includes('</') || str.includes('/>');
}

function parseToJSX(translationString) {
  const doc = new DOMParser().parseFromString(translationString, 'text/html');
  const nodes = doc.body.childNodes;

  const JSXNodes = Array.from(nodes).map((el, i) => {
    if (el.tagName === 'BR') {
      return React.createElement(el.tagName.toLowerCase(), {key: i});
    }

    if (el.tagName) {
      const reactEl = React.createElement(
        el.tagName.toLowerCase(),
        {
          ...getAllAttributes(el),
          key: i,
        },
        el.textContent
      );

      return reactEl;
    }

    return <React.Fragment key={i}>{el.textContent}</React.Fragment>;
  });

  return JSXNodes;
}

function getAllAttributes(el) {
  return el.getAttributeNames().reduce(
    (obj, name) => ({
      ...obj,
      [name]: el.getAttribute(name),
    }),
    {}
  );
}
