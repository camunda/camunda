/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import DownIcon from 'modules/icons/down.svg';

const Select = styled.select`
  width: 100%;
  appearance: none;
  border-radius: 3px;
  box-shadow: ${({theme}) => theme.shadows.select};
  border: solid 1px ${({theme}) => theme.colors.ui03};
  font-size: 13px;
  font-weight: 600;
  color: ${({theme}) => theme.colors.ui07};
  padding: 4px 8px;
  background: ${({theme}) => theme.colors.ui01} url(${DownIcon}) no-repeat;
  background-position: calc(100% - 5px) center;
  outline: none;

  &:focus {
    box-shadow: ${({theme}) => theme.shadows.fakeOutline};
  }
`;

export {Select};
