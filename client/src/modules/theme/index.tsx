/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const palette = {
  blue: '#4d90ff',
  green: '#10d070',
  red: '#ff3d3d',
  orange: '#ffa533',
  ui01: '#f2f3f5',
  ui02: '#f7f8fa',
  ui03: '#b0bac7',
  ui04: '#fdfdfe',
  ui05: '#d8dce3',
  ui06: '#62626e',
  ui07: '#45464e',
} as const;

const theme = {
  colors: {
    ...palette,
    item: {
      odd: palette.ui04,
      even: '#f9fafc',
    },
    smallButton: {
      default: {
        backgroundColor: palette.ui05,
        borderColor: '#9ea9b7',
      },
      hover: {
        backgroundColor: '#cdd4df',
        borderColor: '#9ea9b7',
      },
      active: {
        backgroundColor: palette.ui03,
        borderColor: '#88889a',
      },
      disabled: {
        backgroundColor: '#f1f2f5',
        borderColor: palette.ui03,
      },
    },
    primaryButton: {
      default: {
        backgroundColor: palette.blue,
        borderColor: '#3c85ff',
      },
      hover: {
        backgroundColor: '#3c85ff',
        borderColor: '#1a70ff',
      },
      active: {
        backgroundColor: '#1a70ff',
        borderColor: '#005df7',
      },
      disabled: {
        backgroundColor: '#80b0ff',
        borderColor: '#a2c5ff',
      },
    },
    text: {
      button: 'rgba(69, 70, 78, 0.9)',
      copyrightNotice: 'rgba(98, 98, 110, 0.9)',
      black: '#45464e',
    },
    overlay: 'rgba(255, 255, 255, 0.75)',
    active: '#bcc6d2',
    label01: 'rgba(69, 70, 78, 0.9)', //charcoal-grey-90
    link: {
      active: '#eaf3ff',
    },
  },
  shadows: {
    select: '0 2px 2px 0 rgba(0, 0, 0, 0.08)',
    fakeOutline: '0 0 0 1px #2b7bff, 0 0 0 4px #8cb7ff',
    dropdownMenu: '0 0 2px 0 rgba(0, 0, 0, 0.2)',
    variablesFooter: '0 -1px 2px 0 rgba(0, 0, 0, 0.1)',
    primaryButton: '0 2px 2px 0 rgba(0, 0, 0, 0.35)',
  },
} as const;

export {theme};
