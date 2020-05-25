/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Input = styled.input`
  font-family: IBMPlexSans;
  font-size: 15px;
  width: 322px;
  height: 22px;
  border-radius: 3px;
  background-color: ${({theme}) => theme.colors.ui04};
  border: 1px solid ${({theme}) => theme.colors.ui05};
  padding: 12px 8px;

  &::placeholder {
    font-style: italic;
  }
`;

export {Input};
