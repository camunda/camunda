/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

const Button = styled.button`
  display: flex;
  padding: 0;
  background: transparent;
  border-radius: 50%;

  &:disabled,
  &:disabled :hover {
    cursor: not-allowed;
    svg {
      color: ${({theme}) => theme.colors.ui08};
      opacity: 0.5;
    }

    &:before {
      background-color: transparent;
    }
  }
`;

const Icon = styled.div`
  width: 24px;
  height: 24px;
  border-radius: 50%;

  position: relative;

  &:before {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    position: absolute;
    left: 0;
    content: '';
  }

  &:hover {
    &::before {
      background: ${({theme}) => theme.colors.ui05};
      opacity: 0.5;
      transition: background 0.15s ease-out;
    }
  }

  &:active {
    &::before {
      background: ${({theme}) => theme.colors.ui05};
      opacity: 0.8;
    }
  }
`;

export {Button, Icon};
