import React from 'react';
import {addDecorator} from '@storybook/react';
import '../src/index.scss';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import '@camunda-cloud/common-ui/dist/common-ui/common-ui.css';

Object.defineProperty(window, 'clientConfig', {
  value: {
    ...window.clientConfig,
    canLogout: true,
  },
});

addDecorator((storyFn) => <ThemeProvider>{storyFn()}</ThemeProvider>);
