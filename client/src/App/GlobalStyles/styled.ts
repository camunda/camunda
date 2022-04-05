/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createGlobalStyle, css} from 'styled-components';
import {interactions} from 'modules/theme';

type GlobalStylesProps = {
  tabKeyPressed?: boolean;
};

const GlobalStyles = createGlobalStyle<GlobalStylesProps>`
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

      input[aria-invalid='true'],
      textarea[aria-invalid='true'],
      select[aria-invalid='true'] {
        box-shadow: 0 0 0 1px ${theme.colors.incidentsAndErrors},
          0 0 0 4px ${theme.colors.outlineError};
      }

      input:not(:focus)[aria-invalid='true'],
      textarea:not(:focus)[aria-invalid='true'],
      select:not(:focus)[aria-invalid='true'] {
        border-color: ${theme.colors.incidentsAndErrors};
        box-shadow: none;
      }
    `;
  }}
`;

export {GlobalStyles};
