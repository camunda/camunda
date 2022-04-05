/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import BaseSpinner from 'modules/components/Spinner';

const Backdrop = styled.div`
  ${({theme}) => {
    const colors = theme.colors.variables.backdrop;

    return css`
      background-color: ${colors.backgroundColor};
      z-index: 2;
      display: flex;
      justify-content: center;
      align-items: center;
      width: 100%;
      position: absolute;
      top: 0;
      bottom: 0;
    `;
  }}
`;

const Spinner = styled(BaseSpinner)`
  ${({theme}) => {
    const colors = theme.colors.variables.backdrop.spinner;

    return css`
      height: 15px;
      width: 15px;
      border: 2px solid ${colors.borderColor};
      border-right-color: transparent;
    `;
  }}
`;

export {Backdrop, Spinner};
