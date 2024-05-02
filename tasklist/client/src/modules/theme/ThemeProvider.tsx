/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {
  ThemeProvider as StyledComponentThemeProvider,
} from 'styled-components';
import {CarbonTheme as BaseCarbonTheme} from './CarbonTheme';
import {observer} from 'mobx-react-lite';
import {themeStore} from 'modules/stores/theme';
import {themes, THEME_TOKENS} from './themes';
import {useLayoutEffect} from 'react';
import {usePrefix} from '@carbon/react';

const CarbonTheme = styled(BaseCarbonTheme)`
  width: 100%;
  height: 100%;
` as typeof BaseCarbonTheme;

type Props = {
  children: React.ReactNode;
};

const ThemeProvider: React.FC<Props> = observer(({children}) => {
  const {actualTheme} = themeStore;
  const prefix = usePrefix();

  useLayoutEffect(() => {
    document.documentElement.dataset.carbonTheme = THEME_TOKENS[actualTheme];
  }, [actualTheme, prefix]);

  return (
    <CarbonTheme>
      <StyledComponentThemeProvider theme={themes[THEME_TOKENS[actualTheme]]}>
        {children}
      </StyledComponentThemeProvider>
    </CarbonTheme>
  );
});

export {ThemeProvider};
