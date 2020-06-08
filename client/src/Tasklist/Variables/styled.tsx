/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Title = styled.h1`
  margin: 44px 0 24px 20px;
  font-size: 20px;
  font-weight: 600;
  color: ${({theme}) => theme.colors.ui06};
`;

const EmptyMessage = styled.div`
  margin-left: 20px;
  padding-top: 12px;
  color: ${({theme}) => theme.colors.text.black};
`;

export {Title, EmptyMessage};
