/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {GlobalStyle} from 'modules/theme/GlobalStyle';
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
    const body = document.body;

    Object.values(THEME_TOKENS).forEach((theme) => {
      body.classList.remove(`${prefix}--${theme}`);
    });

    body.classList.add(`${prefix}--${THEME_TOKENS[actualTheme]}`);
  }, [actualTheme, prefix]);

  return (
    <CarbonTheme>
      <StyledComponentThemeProvider theme={themes[THEME_TOKENS[actualTheme]]}>
        <GlobalStyle />
        {children}
      </StyledComponentThemeProvider>
    </CarbonTheme>
  );
});

export {ThemeProvider};
