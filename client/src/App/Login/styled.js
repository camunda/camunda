/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import {ReactComponent as BaseLogo} from 'modules/components/Icon/logo.svg';
import Input from 'modules/components/Input';
import BasicCopyright from 'modules/components/Copyright';

export const Login = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 128px;
  font-family: IBMPlexSans;
  height: 100%;
`;

export const LoginHeader = themed(styled.div`
  align-self: center;
  display: flex;
  flex-direction: column;
  align-items: center;
`);

export const Logo = themed(styled(BaseLogo)`
  margin-bottom: 12px;
  width: 96px;
  height: 33px;

  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06,
  })};
`);

export const LoginTitle = themed(styled.span`
  font-family: IBMPlexSans;
  font-size: 28px;
  font-weight: normal;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06,
  })};
`);

export const LoginForm = styled.form`
  display: flex;
  flex-direction: column;
  margin-top: 53px;
`;

export const FormError = styled.div`
  font-size: 15px;
  font-weight: 500;
  color: ${Colors.incidentsAndErrors};
  margin-bottom: 10px;
  height: 15px;
`;

const LoginInput = styled(Input)`
  height: 48px;
  padding-left: 8px;
  padding-right: 10px;
  padding-top: 12.6px;
  padding-bottom: 16.4px;

  font-size: 15px;
`;

export const UsernameInput = themed(styled(LoginInput)`
  margin-bottom: 16px;
`);

export const PasswordInput = themed(styled(LoginInput)`
  margin-bottom: 32px;
`);

export const Copyright = styled(BasicCopyright)`
  margin-top: auto;
  padding-bottom: 8px;
  padding-top: 70px;
`;
