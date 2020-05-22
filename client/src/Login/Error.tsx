/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Error = styled.span`
  width: 340px;
  color: ${({theme}) => theme.colors.red};
  font-size: 15px;
  text-align: left;
  font-weight: 500;
`;

export {Error};
