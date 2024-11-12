/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {ReactNode, useEffect, useState} from 'react';
import {Loading} from '@carbon/react';

import {getOptimizeVersion} from 'config';
import {loadDateTranslation} from 'dates';

import {loadTranslation} from './service';

type PrimitiveValue = string | number | boolean;

interface TranslationObject {
  [key: string]: PrimitiveValue | TranslationObject;
}

let translationObject: TranslationObject = {};

export function setTranslation(initialTranslationObject: TranslationObject) {
  translationObject = initialTranslationObject;
}

export function TranslationProvider({children}: {children: ReactNode}): JSX.Element {
  const [translationLoaded, setTranslationLoaded] = useState(false);

  useEffect(() => {
    (async () => {
      const localeCode = getLanguage();
      document.documentElement.lang = localeCode;
      await loadDateTranslation(localeCode);
      translationObject = await loadTranslation(await getOptimizeVersion(), localeCode);
      setTranslationLoaded(true);
    })();
  }, []);

  if (!translationLoaded) {
    return <Loading />;
  }

  return <>{children}</>;
}

export function t(key: string, data?: TranslationObject): string | JSX.Element[] {
  const translatedString = injectData(findValue(key, translationObject), data);

  if (containsHTML(translatedString)) {
    return parseToJSX(translatedString);
  }

  return translatedString;
}

export function getLanguage(): string {
  const nav = window.navigator;
  const browserLang = (Array.isArray(nav.languages) ? nav.languages[0] : nav.language || '').split(
    '-'
  );

  return browserLang[0].toLowerCase();
}

function injectData(template: string, data: TranslationObject = {}): string {
  return template.replace(/\{([\w.]*)\}/g, (_str, key) => findValue(key, data));
}

function findValue(key: string, data: TranslationObject = {}): string {
  const keys = key.split('.');
  let v: TranslationObject | PrimitiveValue | undefined = data;
  for (let i = 0; i < keys.length; i++) {
    const currKey = keys[i];
    v = currKey && typeof v === 'object' ? v[currKey] : undefined;
    if (typeof v === 'undefined') {
      throw new Error(`"${keys[i]}" key of "${key}" not found in translation object`);
    }
  }
  return v as string;
}

function containsHTML(str: string): boolean {
  return str.includes('</') || str.includes('/>');
}

function parseToJSX(translationString: string): JSX.Element[] {
  const doc = new DOMParser().parseFromString(translationString, 'text/html');

  const nodes = doc.body.childNodes;

  const JSXNodes = Array.from(nodes).map((el, i) => {
    if (el.nodeType === Node.TEXT_NODE) {
      return <React.Fragment key={i}>{el.textContent}</React.Fragment>;
    }

    if (el.nodeType === Node.ELEMENT_NODE) {
      const element = el as Element;
      if (element.tagName === 'BR') {
        return <br key={i} />;
      }

      const reactEl = React.createElement(
        element.tagName.toLowerCase(),
        {
          ...getAllAttributes(element),
          key: i,
        },
        ...parseToJSX(element.innerHTML)
      );

      return reactEl;
    }

    return <React.Fragment key={i} />;
  });

  return JSXNodes;
}

function getAllAttributes(el: Element): Record<string, string> {
  return el.getAttributeNames().reduce((obj, name) => {
    return {
      ...obj,
      [name]: el.getAttribute(name) ?? '',
    };
  }, {});
}
