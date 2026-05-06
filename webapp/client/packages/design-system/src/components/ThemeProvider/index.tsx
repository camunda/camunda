/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {GlobalTheme} from '@carbon/react';
import {useLayoutEffect, type ReactNode} from 'react';

export type Theme = 'light' | 'dark';

const CARBON_THEME: Record<Theme, string> = {
  light: 'g10',
  dark: 'g100',
};

interface ThemeProviderProps {
  theme: Theme;
  children: ReactNode;
}

function ThemeProvider({theme, children}: ThemeProviderProps) {
  useLayoutEffect(() => {
    document.documentElement.dataset['carbonTheme'] = CARBON_THEME[theme];
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  return <GlobalTheme>{children}</GlobalTheme>;
}

export {ThemeProvider};
