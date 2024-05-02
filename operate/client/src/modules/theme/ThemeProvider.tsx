/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';

import {currentTheme} from 'modules/stores/currentTheme';
import {Theme} from '@carbon/react';

type Props = {
  children?: React.ReactNode;
};

const ThemeProvider = observer<React.FC<Props>>(({children}) => {
  return (
    <Theme
      theme={`${currentTheme.theme === 'light' ? 'g10' : 'g100'}`}
      className="carbonThemeProvider"
    >
      {children}
    </Theme>
  );
});

export {ThemeProvider};
