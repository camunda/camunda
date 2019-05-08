/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {css} from 'styled-components';
import {Colors} from '.';

const ExpandButtonThemes = {
  collapse: {
    default: {
      background: {
        // transparent background
        dark: '',
        light: ''
      },
      arrow: {
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
      arrow: {
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
      arrow: {
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

export default ExpandButtonThemes;
