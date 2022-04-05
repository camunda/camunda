/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {rgba} from 'polished';

import DownIcon from 'modules/icons/down.svg';
import DisabledDownIcon from 'modules/icons/disabled-down.svg';

const Select = styled.select`
  ${({theme}) => {
    return css`
      width: 100%;
      appearance: none;
      border-radius: 3px;
      box-shadow: ${theme.shadows.select};
      border: solid 1px ${theme.colors.ui03};
      font-size: 13px;
      font-weight: 600;
      color: ${theme.colors.ui07};
      padding: 4px 8px;
      background: ${theme.colors.ui01} url(${DownIcon}) no-repeat;
      background-position: calc(100% - 5px) center;
      outline: none;

      &:focus {
        box-shadow: ${theme.shadows.fakeOutline};
      }

      &:disabled {
        border-color: ${rgba(theme.colors.ui03, 0.2)};
        background-color: ${rgba(theme.colors.ui01, 0.4)};
        background-image: url(${DisabledDownIcon});
        color: ${rgba(theme.colors.ui06, 0.7)};
        box-shadow: none;
        font-weight: 400;
      }
    `;
  }}
`;

export {Select};
