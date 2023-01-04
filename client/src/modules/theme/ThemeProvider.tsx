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

const CarbonTheme = styled(BaseCarbonTheme)`
  width: 100%;
  height: 100%;
` as typeof BaseCarbonTheme;

type Props = {
  children: React.ReactNode;
};

const ThemeProvider: React.FC<Props> = observer(({children}) => {
  return (
    <CarbonTheme>
      <StyledComponentThemeProvider
        theme={themes[THEME_TOKENS[themeStore.actualTheme]]}
      >
        <GlobalStyle />
        {children}
      </StyledComponentThemeProvider>
    </CarbonTheme>
  );
});

export {ThemeProvider};
