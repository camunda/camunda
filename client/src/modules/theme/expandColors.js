/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Colors} from './';

const ExpandColors = {
  collapse: {
    default: {
      background: {
        dark: {color: 'transparent', opacity: 1},
        light: {color: 'transparent', opacity: 1}
      },
      arrow: {
        dark: {color: '#ffffff', opacity: 1},
        light: {color: Colors.uiDark04, opacity: 0.9}
      }
    },
    hover: {
      background: {
        dark: {color: '#ffffff', opacity: 0.25},
        light: {color: Colors.uiLight05, opacity: 0.5}
      },
      arrow: {
        dark: {color: Colors.uiLight02, opacity: 1},
        light: {color: Colors.uiDark04, opacity: 0.9}
      }
    },
    active: {
      background: {
        dark: {color: '#ffffff', opacity: 0.4},
        light: {color: Colors.uiLight05, opacity: 0.8}
      },
      arrow: {
        dark: {color: Colors.uiLight02, opacity: 1},
        light: {color: Colors.uiDark04, opacity: 1}
      }
    }
  }
};

export default ExpandColors;
