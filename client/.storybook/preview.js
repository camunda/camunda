import React from 'react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {NotificationProvider} from 'modules/notifications';
import {theme} from 'modules/theme';
import GlobalStyles from 'App/GlobalStyles';
import 'index.css';
import {initializeWorker, mswDecorator} from 'msw-storybook-addon';

initializeWorker();

export const decorators = [
  mswDecorator,
  (storyFn) => {
    return (
      <NotificationProvider>
        <ThemeProvider theme={theme}>
          <GlobalStyles />
          {storyFn()}
        </ThemeProvider>
      </NotificationProvider>
    );
  },
];
