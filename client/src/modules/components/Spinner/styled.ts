/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {keyframes, css} from 'styled-components';

const SpinnerKeyframe = keyframes`
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
`;

const BaseSpinner = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.spinner;

    return css`
      /* intentionally sized based on the parent's font-size */
      width: 1em;
      height: 1em;
      border-radius: 50%;
      position: relative;
      border: 3px solid ${colors.borderColor};
      border-right-color: transparent;
      animation: ${SpinnerKeyframe} 0.7s infinite linear;
    `;
  }}
`;

export {BaseSpinner};
