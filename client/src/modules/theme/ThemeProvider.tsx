/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {ThemeProvider as DefaultProvider} from 'styled-components';
import {observer} from 'mobx-react';

import {theme} from './theme';
import {currentTheme} from 'modules/stores/currentTheme';
import {Theme as BaseCarbonTheme} from '@carbon/react';

type Props = {
  children?: React.ReactNode;
};

const CarbonTheme = styled(BaseCarbonTheme)`
  width: 100%;
  height: 100%;
` as typeof BaseCarbonTheme;

const ThemeProvider = observer<React.FC<Props>>(({children}) => {
  return (
    <CarbonTheme theme="g10">
      <DefaultProvider theme={theme[currentTheme.state.selectedTheme]}>
        {children}
      </DefaultProvider>
    </CarbonTheme>
  );
});

export {ThemeProvider};
