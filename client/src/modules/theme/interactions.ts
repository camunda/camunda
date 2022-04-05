/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {css, ThemedInterpolationFunction} from 'styled-components';

const focusCss: ThemedInterpolationFunction = ({theme}) => {
  const shadow = theme.shadows.modules.focus;

  return css`
    box-shadow: ${shadow};
    outline: none;

    /*
     the transition is here because we want an effect only when we
     enter the element for focus, not for leaving it
    */
    transition: box-shadow 0.05s ease-out;
  `;
};

const focusSelector = () => {
  return css`
    &:focus {
      ${focusCss};
    }
  `;
};

const focus = {
  css: focusCss,
  selector: focusSelector,
};

type ErrorBorderProps = {
  $hasError?: boolean;
};

const errorBorders: ThemedInterpolationFunction<ErrorBorderProps> = ({
  theme,
  $hasError,
}) => {
  return css`
    &:not(:focus) {
      ${$hasError
        ? css`
            border-color: ${theme.colors.incidentsAndErrors};
          `
        : ''}
    }

    &:focus {
      ${$hasError
        ? css`
            box-shadow: 0 0 0 1px ${theme.colors.incidentsAndErrors},
              0 0 0 4px ${theme.colors.outlineError};
          `
        : ''}
    }
  `;
};

export default {focus};
export {errorBorders};
