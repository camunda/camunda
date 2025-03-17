/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n, {type Resource} from 'i18next';
import translationsEn from './locales/en.json';
import translationsFr from './locales/fr.json';
import translationsDe from './locales/de.json';
import translationsEs from './locales/es.json';

import {
  enUS as dateLocaleEnUS,
  fr as dateLocaleFr,
  de as dateLocaleDe,
  es as dateLocaleEs,
  type Locale,
} from 'date-fns/locale';

type LocaleConfig = {
  language: string;
  dateLocale: Locale;
  translationFile: Resource;
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
    dateLocale: dateLocaleEnUS,
    translationFile: translationsEn,
  },
  fr: {
    language: 'Français',
    dateLocale: dateLocaleFr,
    translationFile: translationsFr,
  },
  de: {
    language: 'Deutsch',
    dateLocale: dateLocaleDe,
    translationFile: translationsDe,
  },
  es: {
    language: 'Español',
    dateLocale: dateLocaleEs,
    translationFile: translationsEs,
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

const translationResources: Resource = Object.keys(
  localeDefinitions,
).reduce<Resource>((acc, key: keyof typeof localeDefinitions) => {
  acc[key] = localeDefinitions[key].translationFile;

  return acc;
}, {});

const getCurrentDateLocale = () =>
  localeDefinitions[i18n.language]?.dateLocale ?? dateLocaleEnUS;

export {
  localeDefinitions,
  languageItems,
  translationResources,
  getCurrentDateLocale,
};
