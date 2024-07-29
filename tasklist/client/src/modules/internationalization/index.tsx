/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n from 'i18next';
import translation_en from './locales/en.json';
import translation_fr from './locales/fr.json';
import translation_de from './locales/de.json';
import translation_es from './locales/es.json';

import {
  enUS as dateLocale_enUS,
  fr as dateLocale_fr,
  de as dateLocale_de,
  es as dateLocale_es,
  Locale,
} from 'date-fns/locale';

type LocaleConfig = {
  language: string;
  dateLocale: Locale;
  translationFile: object;
};

export type SelectionOption = {
  id: string;
  label: string;
};

interface LocaleDefinitions {
  [key: string]: LocaleConfig;
}

const localeDefinitions: LocaleDefinitions = {
  en: {
    language: 'English',
    dateLocale: dateLocale_enUS,
    translationFile: translation_en,
  },
  fr: {
    language: 'Français',
    dateLocale: dateLocale_fr,
    translationFile: translation_fr,
  },
  de: {
    language: 'Deutsch',
    dateLocale: dateLocale_de,
    translationFile: translation_de,
  },
  es: {
    language: 'Español',
    dateLocale: dateLocale_es,
    translationFile: translation_es,
  },
};

const languageItems: SelectionOption[] = Object.keys(localeDefinitions).map(
  (key) => {
    return {
      id: key,
      label: localeDefinitions[key].language,
    };
  },
);

const translationResources = Object.keys(localeDefinitions).reduce(
  (acc, key) => {
    acc[key] = localeDefinitions[key].translationFile;
    return acc;
  },
  {} as Record<string, object>,
);

const getCurrentDateLocale = () =>
  localeDefinitions[i18n.language]?.dateLocale || dateLocale_enUS;

export {
  localeDefinitions,
  languageItems,
  translationResources,
  getCurrentDateLocale,
};
