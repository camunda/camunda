/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {createGlobalStyle} from 'styled-components';

import IBMPlexSansBold from '../fonts/IBMPlexSans-Bold.woff2';
import IBMPlexSansItalic from '../fonts/IBMPlexSans-Italic.woff2';
import IBMPlexSansMedium from '../fonts/IBMPlexSans-Medium.woff2';
import IBMPlexSansRegular from '../fonts/IBMPlexSans-Regular.woff2';
import IBMPlexSansSemiBold from '../fonts/IBMPlexSans-SemiBold.woff2';
import IBMPlexSansMono from '../fonts/IBMPlexMono-Regular.woff2';
import {USING_KEYBOARD_CLASS_NAME} from './constans';

const Style = createGlobalStyle`
  @font-face {
    font-family: 'IBMPlexSans';
    src: local('IBMPlexSans-Bold'), url('${IBMPlexSansBold}');
    font-weight: bold;
  }

  @font-face {
    font-family: 'IBMPlexSans';
    src: local('IBMPlexSans-Italic'), url('${IBMPlexSansItalic}');
    font-style: italic;
  }

  @font-face {
    font-family: 'IBMPlexSans';
    src: local('IBMPlexSans-Medium'), url('${IBMPlexSansMedium}');
    font-weight: 500;
  }

  @font-face {
    font-family: 'IBMPlexSans';
    src: local('IBMPlexSans-Regular'), url('${IBMPlexSansRegular}');
  }

  @font-face {
    font-family: 'IBMPlexSans';
    src: local('IBMPlexSans-SemiBold'), url('${IBMPlexSansSemiBold}');
    font-weight: 600;
  }

  @font-face {
    font-family: 'IBMPlexMono';
    src: local('IBMPlexMono-Regular'), url('${IBMPlexSansMono}');
  }

  html, body, #root {
    -moz-osx-font-smoothing: antialiased;
    -webkit-font-smoothing: antialiased;
    height: 100%;
    font-family: IBMPlexSans;
    margin: 0;
    padding: 0;
    background-color: ${({theme}) => theme.colors.ui01};
  }

  a {
    color: currentColor;
    text-decoration: none;
  }

  ul {
    padding: 0px;
    margin: 0px;
    list-style-type: none;
  }

  svg {
    fill: currentColor;
  }

  button {
    font-family: IBMPlexSans;
    cursor: pointer;
    border-width: 0;
  }

  body button:focus,
  body code:focus,
  body a:focus {
    outline: none;
  }

  body.${USING_KEYBOARD_CLASS_NAME} button:focus,
  body.${USING_KEYBOARD_CLASS_NAME} code:focus,
  body.${USING_KEYBOARD_CLASS_NAME} a:focus {
    box-shadow: 0 0 0 1px ${({theme}) =>
      theme.colors.focusInner}, 0 0 0 4px ${({theme}) =>
  theme.colors.focusOuter};
    transition: box-shadow 0.05s ease-out;
  }
`;

export {Style};
