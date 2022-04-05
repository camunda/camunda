/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {rgba} from 'polished';

const OptionButton = styled.button`
  width: 100%;
  padding: 0 10px;
  background: none;
  color: ${({theme}) => rgba(theme.colors.ui06, 0.9)};
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;

  &:hover {
    background: ${({theme}) => theme.colors.ui05};
  }

  &:active {
    background: ${({theme}) => theme.colors.active};
  }
`;

const Li = styled.li`
  &:first-child {
    &:after,
    &:before {
      position: absolute;
      border: solid transparent;
      content: ' ';
      pointer-events: none;
      bottom: 100%;
      right: 15px;
    }

    &:after {
      border-width: 7px;
      margin-right: -7px;
      border-bottom-color: ${({theme}) => theme.colors.ui02};
    }

    &:before {
      border-width: 8px;
      border-bottom-color: ${({theme}) => theme.colors.ui05};
      margin-right: -8px;
    }
  }

  &:first-child:hover {
    &:after {
      border-bottom-color: ${({theme}) => theme.colors.ui05};
    }
  }

  &:first-child:active {
    &:after {
      border-bottom-color: ${({theme}) => theme.colors.active};
    }
  }

  &:not(:last-child) {
    border-bottom: 1px solid ${({theme}) => theme.colors.ui05};
  }

  &:first-child:last-child ${OptionButton} {
    border-radius: 2px 2px 2px 2px;
  }

  &:last-child ${OptionButton} {
    border-radius: 0 0 2px 2px;
  }
  &:first-child ${OptionButton} {
    border-radius: 2px 2px 0 0;
  }
`;

export {OptionButton, Li};
