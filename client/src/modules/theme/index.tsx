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
  white: '#ffffff',
  ui01: '#f2f3f5',
  ui02: '#f7f8fa',
  ui03: '#b0bac7',
  ui04: '#fdfdfe',
  ui05: '#d8dce3',
  ui06: '#62626e',
  ui07: '#45464e',
  ui08: '#5b5e63',
  ui09: '#393a41',
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
        color: 'rgba(69, 70, 78, 0.5)',
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
    icon: {
      hover: {
        backgroundColor: palette.ui05,
        borderColor: '#9ea9b7',
      },
      active: {
        backgroundColor: '#d3d6e0',
        borderColor: '#f1f2f5',
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
    input: {
      placeholder: 'rgba(98, 98, 110, 0.9)',
    },
  },
  shadows: {
    select: '0 2px 2px 0 rgba(0, 0, 0, 0.08)',
    fakeOutline: '0 0 0 1px #2b7bff, 0 0 0 4px #8cb7ff',
    dropdownMenu: '0 0 2px 0 rgba(0, 0, 0, 0.2)',
    variablesFooter: '0 -1px 2px 0 rgba(0, 0, 0, 0.1)',
    primaryButton: '0 2px 2px 0 rgba(0, 0, 0, 0.35)',
    invalidInput: `0 0 0 1px ${palette.red}, 0 0 0 4px #ffafaf`,
  },
} as const;

export {theme};
