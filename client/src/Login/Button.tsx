/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Button = styled.button`
  cursor: pointer;
  color: ${({theme}) => theme.colors.text.button};
  font-weight: 600;
  font-size: 18px;
  width: 340px;
  height: 48px;
  background-color: ${({theme}) => theme.colors.ui[4]};
  border: 1px solid ${({theme}) => theme.colors.ui[2]};
  border-radius: 3px;
  text-align: center;
`;

export {Button};
