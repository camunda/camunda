/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {rgba} from 'polished';
import {ReactComponent} from 'modules/icons/logo.svg';

const Button = styled.button`
  cursor: pointer;
  color: ${({theme}) => theme.colors.button.large.default.color};
  font-weight: 600;
  font-size: 18px;
  width: 340px;
  height: 48px;
  background-color: ${({theme}) =>
    theme.colors.button.large.default.backgroundColor};
  border: 1px solid ${({theme}) => theme.colors.button.large.default.border};
  box-shadow: ${({theme}) => theme.shadows.button.large};
  border-radius: 3px;
  text-align: center;

  &:hover {
    background-color: ${({theme}) =>
      theme.colors.button.large.hover.backgroundColor};
    border: 1px solid ${({theme}) => theme.colors.button.large.hover.border};
  }

  &:active {
    color: ${({theme}) => theme.colors.button.large.active.color};
    background-color: ${({theme}) =>
      theme.colors.button.large.active.backgroundColor};
    border: 1px solid ${({theme}) => theme.colors.button.large.active.border};
  }

  &:disabled {
    color: ${({theme}) => theme.colors.button.large.disabled.color};
    background-color: ${({theme}) =>
      theme.colors.button.large.disabled.backgroundColor};
    border: 1px solid ${({theme}) => theme.colors.button.large.disabled.border};
    box-shadow: none;
    cursor: not-allowed;
  }
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
  line-height: 15px;
`;

const Logo = styled(ReactComponent)`
  fill: ${({theme}) => theme.colors.ui06};
`;

const Title = styled.h1`
  color: ${({theme}) => theme.colors.ui06};
  font-weight: normal;
`;

const Input = styled.input`
  font-family: IBM Plex Sans;
  font-size: 15px;
  width: 322px;
  height: 22px;
  border-radius: 3px;
  background-color: ${({theme}) => theme.colors.ui04};
  border: 1px solid ${({theme}) => theme.colors.ui05};
  padding: 12px 8px;
  color: ${({theme}) => theme.colors.ui09};

  &::placeholder {
    font-style: italic;
    color: ${({theme}) => rgba(theme.colors.ui06, 0.9)};
  }

  &:focus {
    outline: none;
    box-shadow: ${({theme}) => theme.shadows.fakeOutline};
    transition: box-shadow 0.05s ease-out;
  }
`;

interface FormContainerProps {
  hasError: boolean;
}
const FormContainer = styled.div<FormContainerProps>`
  display: flex;
  flex-direction: column;
  align-items: center;

  & > ${Logo} {
    margin-top: 200px;
  }

  & > ${Title} {
    margin-top: 12px;
    margin-bottom: ${({hasError}) => (hasError ? 53 : 78)}px;
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
