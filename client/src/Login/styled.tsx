/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {ReactComponent} from 'modules/icons/logo.svg';

const Button = styled.button`
  cursor: pointer;
  color: ${({theme}) => theme.colors.text.button};
  font-weight: 600;
  font-size: 18px;
  width: 340px;
  height: 48px;
  background-color: ${({theme}) => theme.colors.ui05};
  border: 1px solid ${({theme}) => theme.colors.ui03};
  border-radius: 3px;
  text-align: center;
`;

const Container = styled.main`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  height: 100%;
`;

const CopyrightNotice = styled.span`
  color: ${({theme}) => theme.colors.text.copyrightNotice};
  font-size: 12px;
  padding-bottom: 10px;
`;

const Error = styled.span`
  width: 340px;
  color: ${({theme}) => theme.colors.red};
  font-size: 15px;
  text-align: left;
  font-weight: 500;
`;

const Logo = styled(ReactComponent)`
  fill: ${({theme}) => theme.colors.ui06};
`;

const Title = styled.h1`
  color: ${({theme}) => theme.colors.ui06};
  font-weight: normal;
`;

const Input = styled.input`
  font-family: IBMPlexSans;
  font-size: 15px;
  width: 322px;
  height: 22px;
  border-radius: 3px;
  background-color: ${({theme}) => theme.colors.ui04};
  border: 1px solid ${({theme}) => theme.colors.ui05};
  padding: 12px 8px;

  &::placeholder {
    font-style: italic;
  }
`;

const FormContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;

  & > ${Logo} {
    margin-top: 200px;
  }

  & > ${Title} {
    margin-top: 12px;
    margin-bottom: 78px;
  }

  & > ${Error} {
    margin-bottom: 10px;
  }

  & > ${Input}:first-of-type {
    margin-bottom: 16px;
  }

  & > ${Input}:last-of-type {
    margin-bottom: 32px;
  }
`;

export {
  Input,
  Logo,
  Title,
  Error,
  FormContainer,
  CopyrightNotice,
  Container,
  Button,
};
