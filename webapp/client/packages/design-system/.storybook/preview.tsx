/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Preview} from '@storybook/react';
import {ThemeProvider} from '../src/components/ThemeProvider';
import '../carbon.scss';
import './preview.css';

export const globalTypes = {
  theme: {
    name: 'Theme',
    defaultValue: 'light',
    toolbar: {
      icon: 'contrast',
      items: [
        {value: 'light', title: 'Light'},
        {value: 'dark', title: 'Dark'},
      ],
      dynamicTitle: true,
    },
  },
};

const preview: Preview = {
  decorators: [
    (Story, context) => (
      <ThemeProvider theme={context.globals['theme'] ?? 'light'}>
        <div className="bg-background text-foreground min-h-screen p-4">
          <Story />
        </div>
      </ThemeProvider>
    ),
  ],
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    options: {
      storySort: (a, b) => {
        const aIsDoc = a.type === 'docs';
        const bIsDoc = b.type === 'docs';
        if (aIsDoc !== bIsDoc) return aIsDoc ? 1 : -1;
        return 0;
      },
    },
  },
};

export default preview;
