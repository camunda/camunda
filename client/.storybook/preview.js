import React from 'react';
import {addDecorator} from '@storybook/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

import {theme} from 'modules/theme';
import GlobalStyles from 'App/GlobalStyles';
import 'index.css';
import {initializeWorker, mswDecorator} from 'msw-storybook-addon';
initializeWorker();
addDecorator(mswDecorator);

addDecorator((storyFn) => {
  return (
    <ThemeProvider theme={theme}>
      <GlobalStyles />
      {storyFn()}
    </ThemeProvider>
  );
});
