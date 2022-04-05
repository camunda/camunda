/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rgba} from 'polished';

const palette = {
  blue: '#4d90ff',
  green: '#10d070',
  red: '#ff3d3d',
  orange: '#ffa533',
  white: '#fff',
  ui01: '#f2f3f5',
  ui02: '#f7f8fa',
  ui03: '#b0bac7',
  ui04: '#fdfdfe',
  ui05: '#d8dce3',
  ui06: '#62626e',
  ui07: '#45464e',
  ui08: '#5b5e63',
  ui09: '#393a41',
  black: '#000',
  focusInner: '#2b7bff',
  focusOuter: '#8cb7ff',
  linkDefault: '#346ac4',
  linkHover: '#4b7ccf',
  linkActive: '#29549c',
  linkVisited: '#a846fe',
} as const;

const theme = {
  colors: {
    ...palette,
    item: {
      odd: palette.ui04,
      even: '#f9fafc',
    },
    button: {
      small: {
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
      primary: {
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
      large: {
        default: {
          color: rgba(palette.ui07, 0.9),
          backgroundColor: palette.ui05,
          border: palette.ui03,
        },
        hover: {
          backgroundColor: '#cdd4df',
          border: '#9ea9b7',
        },
        active: {
          color: 'rgba(49, 50, 56, 0.9)',
          border: '#88889a',
          backgroundColor: palette.ui03,
        },
        disabled: {
          color: rgba(palette.ui07, 0.5),
          border: palette.ui03,
          backgroundColor: '#f1f2f5',
        },
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
      copyrightNotice: 'rgba(98, 98, 110, 0.9)',
      black: '#45464e',
    },
    hint: {
      color: palette.blue,
    },
    overlay: 'rgba(255, 255, 255, 0.75)',
    active: '#bcc6d2',
    label01: 'rgba(69, 70, 78, 0.9)', //charcoal-grey-90
    link: {
      active: '#eaf3ff',
    },
    login: {
      input: {
        backgroundColor: palette.ui04,
        borderColor: palette.ui03,
        color: palette.ui07,
        labelColor: palette.ui06,
        focusInner: palette.focusInner,
        focusOuter: palette.focusOuter,
      },
    },
    disclaimer: {
      container: {
        color: '#7e7e7f',
      },
    },
    header: {
      separator: palette.ui05,
      license: {
        color: palette.ui07,
        backgroundColor: palette.ui02,
        borderColor: palette.ui05,
        arrowStyle: {
          before: {
            borderColor: palette.ui05,
          },
          after: {
            borderColor: palette.ui02,
          },
        },
      },
    },
  },
  shadows: {
    select: `0 2px 2px 0 ${rgba(palette.black, 0.08)}`,
    fakeOutline: '0 0 0 1px #2b7bff, 0 0 0 4px #8cb7ff',
    dropdownMenu: `0 0 2px 0 ${rgba(palette.black, 0.2)}`,
    detailsFooter: `0 -1px 2px 0 ${rgba(palette.black, 0.1)}`,
    invalidInput: `0 0 0 1px ${palette.red}, 0 0 0 4px #ffafaf`,
    button: {
      large: `0 2px 2px 0 ${rgba(palette.black, 0.08)}`,
      primary: `0 2px 2px 0 ${rgba(palette.black, 0.35)}`,
    },
  },
} as const;

export {theme};
