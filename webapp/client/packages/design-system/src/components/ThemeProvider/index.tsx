/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {GlobalTheme} from '@carbon/react';
import {useLayoutEffect, type ReactNode} from 'react';

export type ThemeMode = 'light' | 'dark';

const CARBON_THEME = {
  light: 'g10',
  dark: 'g100',
} as const satisfies Record<ThemeMode, 'g10' | 'g100'>;

interface ThemeProviderProps {
  theme: ThemeMode;
  children: ReactNode;
}

function ThemeProvider({theme, children}: ThemeProviderProps) {
  const carbonTheme = CARBON_THEME[theme];

  useLayoutEffect(() => {
    document.documentElement.dataset['carbonTheme'] = carbonTheme;
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme, carbonTheme]);

  return <GlobalTheme theme={carbonTheme}>{children}</GlobalTheme>;
}

export {ThemeProvider};
