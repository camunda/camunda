/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';
import {tracking} from 'modules/tracking';

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

  constructor() {
    makeAutoObservable(this);
  }

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
