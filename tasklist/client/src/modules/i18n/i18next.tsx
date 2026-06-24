/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import {initReactI18next} from 'react-i18next';
import {translationResources} from '.';

const detection = {
  order: ['localStorage', 'navigator'],
  lookupLocalStorage: 'language',
  caches: ['localStorage'],
  htmlTag: document.documentElement,
  checkWhitelist: true,
};

function initI18next() {
  i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      detection,
      fallbackLng: 'en',
      resources: translationResources,
      interpolation: {
        escapeValue: false,
      },
    });
}

export {initI18next};
