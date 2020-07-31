/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

type FormProps = {
  hasFooter?: boolean;
};

const Form = styled.form<FormProps>`
  display: grid;
  grid-template-columns: 100%;
  grid-template-rows: ${({hasFooter}) => (hasFooter ? '1fr 62px' : '1fr')};
`;

const Footer = styled.div`
  background-color: ${({theme}) => theme.colors.ui02};
  box-shadow: ${({theme}) => theme.shadows.variablesFooter};
  text-align: right;
  padding: 14px 19px;
`;

export {Footer, Form};
