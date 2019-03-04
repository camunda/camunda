/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import styled from 'styled-components';
import {ThemeConsumer, Colors, themed, themeStyle} from 'modules/theme';

export const Toggle = themed(styled.button`
  position: fixed;
  padding: 10px;
  bottom: 0;
  left: 0;
  background: ${themeStyle({
    dark: Colors.uiLight01,
    light: Colors.uiDark01
  })};
  z-index: 99;
`);

// Utility component to test the theming during development;
export default function ThemeToggle() {
  return (
    <ThemeConsumer>
      {({toggleTheme}) => (
        <React.Fragment>
          <Toggle
            onClick={toggleTheme}
            title="Toggle theme"
            aria-label="Toggle theme"
          />
        </React.Fragment>
      )}
    </ThemeConsumer>
  );
}
