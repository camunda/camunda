/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Table = styled.table`
  width: 100%;
  font-size: 14px;
  border-collapse: collapse;
`;

const TH = styled.th`
  width: 250px;
  padding: 12px 20px;
  text-align: left;
  color: ${({theme}) => theme.colors.text.button};
`;

const TD = styled.td`
  padding: 12px;
  color: ${({theme}) => theme.colors.ui06};
`;

const TR = styled.tr`
  border-bottom: 1px solid ${({theme}) => theme.colors.ui05};
`;

export {Table, TH, TD, TR};
