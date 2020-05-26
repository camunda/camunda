/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const OptionButton = styled.button`
  /* Display & Box Model */
  width: 100%;
  padding: 0 10px;
  background: none;

  /* Color */
  color: ${({theme}) => theme.colors.ui06};

  /* Text */
  text-align: left;
  font-size: 15px;
  font-weight: 600;
  line-height: 36px;

  /* Other */
  &:hover {
    background: ${({theme}) => theme.colors.ui05};
  }

  &:active {
    background: ${({theme}) => theme.colors.active};
  }
`;

const topPointer = css`
  &:first-child {
    /* Pointer Styles */
    &:after,
    &:before {
      position: absolute;
      border: solid transparent;
      content: ' ';
      pointer-events: none;
      bottom: 100%;
      right: 15px;
    }

    /* Pointer Body */
    &:after {
      border-width: 7px;
      margin-right: -7px;
      border-bottom-color: ${({theme}) => theme.colors.ui02};
    }

    /* Pointer Shadow */
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
`;

const Li = styled.li`
  ${topPointer}

  /* Add Border between options */
  &:not(:last-child) {
    border-bottom: 1px solid ${({theme}) => theme.colors.ui05};
  }

  /* Border radius if only one child exists */
  &:first-child:last-child > div > button {
    border-radius: 2px 2px 2px 2px;
  }

  /* Border radius of child button in all states */
  &:last-child > div > button {
    border-radius: 0 0 2px 2px;
  }
  &:first-child > div > button {
    border-radius: 2px 2px 0 0;
  }
`;

export {OptionButton, Li};
