/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createGlobalStyle, css} from 'styled-components';
import {interactions} from 'modules/theme';

const GlobalStyles = createGlobalStyle`
  ${({theme, tabKeyPressed}) => {
    return css`
      body {
        background-color: ${theme.colors.ui01};
      }

      /*
        these elements have custom styling for :focus only on keyboard focus,
        not on mouse click (clicking them does not show the focus style)
      */
      button:focus,
      code:focus,
      a:focus {
        ${tabKeyPressed
          ? interactions.focus.css
          : css`
              outline: none;
            `};
      }

      /*
        these elements have custom styling for :focus on keyboard & mouse focus,
        (clicking them shows the focus style)
      */
      input,
      textarea,
      select {
        ${interactions.focus.selector};
      }
    `;
  }}
`;

export {GlobalStyles};
