/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {action, computed, makeObservable, observable} from 'mobx';
import {getStateLocally, storeStateLocally} from 'common/local-storage';

type ThemeOption = 'system' | 'dark' | 'light';

const STORED_THEME = getStateLocally('theme');
const INITIAL_THEME = isThemeOption(STORED_THEME) ? STORED_THEME : 'system';

function isThemeOption(theme: unknown): theme is ThemeOption {
  return ['system', 'dark', 'light'].includes(theme as string);
}

class Theme {
  selectedTheme: ThemeOption = INITIAL_THEME;
  #systemDefault: 'dark' | 'light' = window.matchMedia(
    '(prefers-color-scheme: dark)',
  ).matches
    ? 'dark'
    : 'light';

  constructor() {
    makeObservable(this, {
      selectedTheme: observable,
      changeTheme: action,
      actualTheme: computed,
      reset: action,
    });
  }

  changeTheme = (theme: ThemeOption) => {
    this.selectedTheme = theme;
    storeStateLocally('theme', theme);
  };

  get actualTheme() {
    return this.selectedTheme === 'system'
      ? this.#systemDefault
      : this.selectedTheme;
  }

  reset = () => {
    this.selectedTheme = INITIAL_THEME;
  };
}

const themeStore = new Theme();

export {themeStore};
