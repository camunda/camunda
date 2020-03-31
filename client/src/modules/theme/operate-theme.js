/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// temporary file to illustrate passing down theme with context
import Colors from './colors';
const operateTheme = {
  dark: {
    colors: {
      primary: Colors.uiDark01,
    },
  },
  light: {
    colors: {
      primary: Colors.uiDark02,
    },
  },
};

export default operateTheme;
