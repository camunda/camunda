/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const PanelTitle = styled.h1`
  font-size: 20px;
  font-weight: 600;
  color: ${({theme}) => theme.colors.ui06};
  margin: 0;
`;

export {PanelTitle};
