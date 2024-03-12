/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

export {currentTheme, THEME_NAME};
export type {ThemeType};
