import React from 'react';
import {addDecorator} from '@storybook/react';
import {ThemeProvider} from 'styled-components';
import {MemoryRouter} from 'react-router-dom';

import {theme} from 'modules/theme';
import {GlobalStyle} from 'GlobalStyle';

addDecorator((storyFn) => (
  <MemoryRouter>
    <ThemeProvider theme={theme}>
      <GlobalStyle />
      {storyFn()}
    </ThemeProvider>
  </MemoryRouter>
));
