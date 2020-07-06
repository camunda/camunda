/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Form = styled.form`
  display: grid;
  grid-template-columns: 100%;
  grid-template-rows: auto 1fr 62px;
`;

const Footer = styled.div`
  background-color: ${({theme}) => theme.colors.ui02};
  box-shadow: ${({theme}) => theme.shadows.variablesFooter};
  text-align: right;
  padding: 14px;
`;

export {Footer, Form};
