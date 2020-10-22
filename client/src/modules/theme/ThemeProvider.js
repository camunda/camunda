/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ThemeProvider as DefaultProvider} from 'styled-components';
import {observer} from 'mobx-react';

import {theme} from './theme';
import {currentTheme} from 'modules/stores/currentTheme';

const ThemeProvider = observer(({children}) => {
  return (
    <DefaultProvider theme={theme[currentTheme.state.selectedTheme]}>
      {children}
    </DefaultProvider>
  );
});

export {ThemeProvider};
