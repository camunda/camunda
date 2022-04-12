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
import {theme} from './index';
import {Theme as BaseCarbonTheme} from '@carbon/react';

const CarbonTheme = styled(BaseCarbonTheme)`
  width: 100%;
  height: 100%;
` as typeof BaseCarbonTheme;

type Props = {
  children: React.ReactNode;
};

const ThemeProvider: React.FC<Props> = ({children}) => {
  return (
    <CarbonTheme theme="g10">
      <StyledComponentThemeProvider theme={theme}>
        <GlobalStyle />
        {children}
      </StyledComponentThemeProvider>
    </CarbonTheme>
  );
};

export {ThemeProvider};
