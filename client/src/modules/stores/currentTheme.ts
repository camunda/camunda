/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';
import {USE_NEW_APP_HEADER} from 'modules/feature-flags';
import {tracking} from 'modules/tracking';

import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type ThemeType = 'light' | 'dark' | 'system';
type State = {
  selectedTheme: ThemeType;
  systemPreference: 'light' | 'dark';
};

const STORAGE_KEY = 'theme';
const THEME_NAME = {
  LIGHT: 'light',
  DARK: 'dark',
  SYSTEM: 'system',
} as const;

const STORED_THEME: ThemeType = getStateLocally()[STORAGE_KEY];
const INITIAL_STATE: State = {
  selectedTheme: Object.values(THEME_NAME).includes(STORED_THEME)
    ? STORED_THEME
    : USE_NEW_APP_HEADER
    ? THEME_NAME.SYSTEM
    : THEME_NAME.LIGHT,
  systemPreference: window.matchMedia?.('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light',
};

class CurrentTheme {
  state: State = INITIAL_STATE;

  constructor() {
    makeAutoObservable(this);
    window
      .matchMedia?.('(prefers-color-scheme: dark)')
      .addEventListener('change', (event) => {
        this.updateSystemPreference(event.matches ? 'dark' : 'light');
      });
  }

  get theme() {
    if (this.state.selectedTheme === 'system') {
      return this.state.systemPreference;
    }

    return this.state.selectedTheme;
  }

  changeTheme = (theme: ThemeType) => {
    tracking.track({
      eventName: 'theme-toggle',
      toggledTo: theme,
    });

    this.state.selectedTheme = theme;

    storeStateLocally({
      [STORAGE_KEY]: theme,
    });
  };

  updateSystemPreference = (theme: 'light' | 'dark') => {
    this.state.systemPreference = theme;
  };

  toggle = () => {
    const nextTheme =
      this.state.selectedTheme === THEME_NAME.LIGHT
        ? THEME_NAME.DARK
        : THEME_NAME.LIGHT;

    tracking.track({
      eventName: 'theme-toggle',
      toggledTo: nextTheme,
    });

    this.state.selectedTheme = nextTheme;
    storeStateLocally({
      [STORAGE_KEY]: nextTheme,
    });
  };

  reset = () => {
    this.state = INITIAL_STATE;
  };
}

const currentTheme = new CurrentTheme();

export {currentTheme, THEME_NAME};
export type {ThemeType};
