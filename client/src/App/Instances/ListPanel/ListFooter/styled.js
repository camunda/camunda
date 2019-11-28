/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import BasicCopyright from 'modules/components/Copyright';

export const Footer = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  height: 100%;

  padding: 0 20px;
  margin-bottom: 1px;

  div {
    flex-grow: 1;
    flex-basis: 0;
  }
`;

export const Copyright = styled(BasicCopyright)`
  text-align: right;
`;
