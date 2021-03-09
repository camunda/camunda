/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {LoadingOverlay as OriginalLoadingOverlay} from 'modules/components/LoadingOverlay';

type FormProps = {
  hasFooter?: boolean;
};

const Form = styled.form<FormProps>`
  display: grid;
  grid-template-columns: 100%;
  grid-template-rows: ${({hasFooter}) => (hasFooter ? '1fr 62px' : '1fr')};
  overflow-y: hidden;
`;

const Footer = styled.div`
  background-color: ${({theme}) => theme.colors.ui02};
  box-shadow: ${({theme}) => theme.shadows.variablesFooter};
  text-align: right;
  padding: 14px 19px;
`;

const Container = styled.div`
  width: 100%;
  height: 100%;
  display: grid;
  height: 100%;
  grid-template-rows: auto 1fr;
  position: relative;
`;

const LoadingOverlay = styled(OriginalLoadingOverlay)`
  align-items: flex-start;
  padding-top: 12.5%;
  position: absolute;
  z-index: 2; // TODO - Remove on issue #676
`;

export {Footer, Form, Container, LoadingOverlay};
