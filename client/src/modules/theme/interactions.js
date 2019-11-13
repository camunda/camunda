/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {css} from 'styled-components';
import {Colors, themeStyle} from 'modules/theme';

const focusTransition = 'box-shadow 0.05s ease-out';
const focusCss = theme => {
  return css`
    box-shadow: ${themeStyle({
      dark: `0 0 0 1px ${Colors.lightFocusInner}, 0 0 0 4px ${Colors.focusOuter}`,
      light: `0 0 0 1px ${Colors.darkFocusInner}, 0 0 0 4px ${Colors.focusOuter}`
    })};
    outline: none;

    // the transition is here because we want an effect only when we
    // enter the element for focus, not for leaving it
    transition: ${focusTransition};
  `;
};

const focusSelector = theme => {
  return css`
    &:focus {
      ${focusCss};
    }
  `;
};

const focus = {
  css: focusCss,
  selector: focusSelector
};

export const errorBorders = css`
  &:not(:focus) {
    ${props => props.hasError && `border-color: ${Colors.incidentsAndErrors};`}
  }

  &:focus {
    ${props =>
      props.hasError &&
      `box-shadow: 0 0 0 1px ${Colors.incidentsAndErrors}, 0 0 0 4px #ffafaf; 
  `}
  }
`;

export default {focus};
