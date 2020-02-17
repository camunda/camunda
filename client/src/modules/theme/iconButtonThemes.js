/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {css} from 'styled-components';
import {Colors} from '.';

const DEFAULT_THEME = 'default';

export function getIconButtonTheme(themeName) {
  return IconButtonThemes[themeName] || IconButtonThemes[DEFAULT_THEME];
}

const IconButtonThemes = {
  // default is used for the incidents panels on dashbord and for variable edit buttons
  default: {
    default: {
      background: {
        // transparent background
        dark: '',
        light: ''
      },
      icon: {
        dark: css`
          color: #ffffff;
        `,
        light: css`
          color: ${Colors.uiDark04};
          opacity: 0.9;
        `
      }
    },
    hover: {
      background: {
        dark: css`
          background: #ffffff;
          opacity: 0.25;
        `,
        light: css`
          background: ${Colors.uiLight05};
          opacity: 0.5;
        `
      },
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiDark04};
          opacity: 0.9;
        `
      }
    },
    active: {
      background: {
        dark: css`
          background: #ffffff;
          opacity: 0.4;
        `,
        light: css`
          background: ${Colors.uiLight05};
          opacity: 0.8;
        `
      },
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiDark04};
        `
      }
    }
  },
  // incidentsBanner is used in the red incidents banner in instance view
  incidentsBanner: {
    default: {
      // transparent background
      background: {dark: '', light: ''},
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiLight02};
        `
      }
    },
    hover: {
      background: {
        dark: css`
          background: #ffffff;
          opacity: 0.25;
        `,
        light: css`
          background: #ffffff;
          opacity: 0.25;
        `
      },
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiLight02};
        `
      }
    },
    active: {
      background: {
        dark: css`
          background: #ffffff;
          opacity: 0.4;
        `,
        light: css`
          background: #ffffff;
          opacity: 0.4;
        `
      },
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiLight02};
        `
      }
    }
  },
  // foldable is used in the history tree view
  foldable: {
    default: {
      // transparent background
      background: {dark: '', light: ''},
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiDark04};
          opacity: 0.9;
        `
      }
    },
    hover: {
      background: {
        dark: css`
          background: #ffffff;
          opacity: 0.25;
        `,
        light: css`
          background: ${Colors.uiLight05};
          opacity: 0.5;
        `
      },
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiDark04};
          opacity: 0.9;
        `
      }
    },
    active: {
      background: {
        dark: css`
          background: #ffffff;
          opacity: 0.4;
        `,
        light: css`
          background: ${Colors.uiLight05};
          opacity: 0.8;
        `
      },
      icon: {
        dark: css`
          color: ${Colors.uiLight02};
        `,
        light: css`
          color: ${Colors.uiDark04};
        `
      }
    }
  }
};

export default IconButtonThemes;
