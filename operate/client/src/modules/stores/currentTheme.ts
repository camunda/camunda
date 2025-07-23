/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
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
    : THEME_NAME.SYSTEM,
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

export {currentTheme};
