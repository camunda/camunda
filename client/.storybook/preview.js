import React from 'react';
import {addDecorator} from '@storybook/react';
import {ThemeProvider} from 'styled-components';
import {MemoryRouter} from 'react-router-dom';

import {theme} from 'modules/theme';
import {GlobalStyle} from 'GlobalStyle';
import '@camunda-cloud/common-ui/dist/common-ui/common-ui.css';

Object.defineProperty(window, 'clientConfig', {
  value: {
    ...window.clientConfig,
    canLogout: true,
  },
});

addDecorator((storyFn) => (
  <ThemeProvider theme={theme}>
    <GlobalStyle />
    {storyFn()}
  </ThemeProvider>
));
