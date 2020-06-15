/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import styled from 'styled-components';

const EmptyMessage = styled.div`
  border: 1px solid ${({theme}) => theme.colors.ui05};
  border-radius: 3px;

  margin: 16px 20px 0 20px;
  padding: 38px 39px;
  text-align: center;
  font-size: 14px;
  color: ${({theme}) => theme.colors.ui07};
  background-color: ${({theme}) => theme.colors.ui04};
`;

export {EmptyMessage};
