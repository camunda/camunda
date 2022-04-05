/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {createGlobalStyle} from 'styled-components';

import {USING_KEYBOARD_CLASS_NAME} from './constants';

const Style = createGlobalStyle`
  html, body, #root {
    -moz-osx-font-smoothing: antialiased;
    -webkit-font-smoothing: antialiased;
    height: 100%;
    overflow: hidden;
    font-family: IBM Plex Sans;
    margin: 0;
    padding: 0;
    background-color: ${({theme}) => theme.colors.ui01};
  }

  a {
    color: currentColor;
    text-decoration: none;
  }

  ul:not(.fjs-form ul) {
    padding: 0px;
    margin: 0px;
    list-style-type: none;
  }

  svg {
    fill: currentColor;
  }

  button {
    font-family: IBM Plex Sans;
    cursor: pointer;
    border-width: 0;
    font-weight: 600;

    &::-moz-focus-inner {
      border: 0;
    }
  }

  body button:focus,
  body code:focus,
  body a:focus {
    outline: none;
  }

  body.${USING_KEYBOARD_CLASS_NAME} button:focus,
  body.${USING_KEYBOARD_CLASS_NAME} code:focus,
  body.${USING_KEYBOARD_CLASS_NAME} a:focus {
    box-shadow: ${({theme}) => theme.shadows.fakeOutline};
    transition: box-shadow 0.05s ease-out;
  }
`;

export {Style};
