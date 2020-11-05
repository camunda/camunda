/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action} from 'mobx';

import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

type ThemeType = 'light' | 'dark';
type State = {
  selectedTheme: 'light' | 'dark';
};

const STORAGE_KEY = 'theme';
const THEME_NAME = {
  LIGHT: 'light',
  DARK: 'dark',
} as const;
const STORED_THEME: ThemeType = getStateLocally()[STORAGE_KEY];
const INITIAL_STATE: State = {
  selectedTheme: Object.values(THEME_NAME).includes(STORED_THEME)
    ? STORED_THEME
    : THEME_NAME.LIGHT,
};

class CurrentTheme {
  state: State = INITIAL_STATE;

  toggle = () => {
    const nextTheme =
      this.state.selectedTheme === THEME_NAME.LIGHT
        ? THEME_NAME.DARK
        : THEME_NAME.LIGHT;

    this.state.selectedTheme = nextTheme;
    storeStateLocally({
      [STORAGE_KEY]: nextTheme,
    });
  };

  reset = () => {
    this.state = INITIAL_STATE;
  };
}

decorate(CurrentTheme, {
  state: observable,
  toggle: action,
  reset: action,
});

const currentTheme = new CurrentTheme();

export {currentTheme, THEME_NAME};
