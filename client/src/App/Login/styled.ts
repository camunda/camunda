/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as BaseLogo} from 'modules/components/Icon/logo.svg';
import {Input} from 'modules/components/Input';
import BasicCopyright from 'modules/components/Copyright';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 128px;
  font-family: IBMPlexSans;
  height: 100%;
`;

const LoginHeader = styled.div`
  align-self: center;
  display: flex;
  flex-direction: column;
  align-items: center;
`;

const Logo = styled(BaseLogo)`
  ${({theme}) => {
    const colors = theme.colors.login.logo;

    return css`
      margin-bottom: 12px;
      width: 96px;
      height: 33px;
      color: ${colors.color};
    `;
  }}
`;

const LoginTitle = styled.span`
  ${({theme}) => {
    const colors = theme.colors.login.loginTitle;

    return css`
      font-family: IBMPlexSans;
      font-size: 28px;
      font-weight: normal;
      color: ${colors.color};
    `;
  }}
`;

const LoginForm = styled.form`
  display: flex;
  flex-direction: column;
  margin-top: 53px;
`;

const FormError = styled.div`
  ${({theme}) => {
    return css`
      font-size: 15px;
      font-weight: 500;
      color: ${theme.colors.incidentsAndErrors};
      margin-bottom: 10px;
      height: 15px;
    `;
  }}
`;

const LoginInput = styled(Input)`
  height: 48px;
  padding-left: 8px;
  padding-right: 10px;
  padding-top: 12.6px;
  padding-bottom: 16.4px;
  font-size: 15px;
`;

const UsernameInput = styled(LoginInput)`
  margin-bottom: 16px;
`;

const PasswordInput = styled(LoginInput)`
  margin-bottom: 32px;
`;

const Copyright = styled(BasicCopyright)`
  margin-top: auto;
  padding-bottom: 8px;
  padding-top: 70px;
`;

export {
  Container,
  LoginHeader,
  Logo,
  LoginTitle,
  LoginForm,
  FormError,
  UsernameInput,
  PasswordInput,
  Copyright,
};
