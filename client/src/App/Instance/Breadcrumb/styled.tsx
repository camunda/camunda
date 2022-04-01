/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Link as LinkComponent} from 'modules/components/Link';

const Container = styled.div`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text02};
      padding: 4px 20px;
      display: flex;
      font-size: 13px;
      align-items: center;
      border-bottom: 1px solid ${theme.colors.borderColor};
      min-height: 30px;
    `;
  }}
`;

const ellipsisCss = css`
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
`;

const Link = styled(LinkComponent)`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text02};
      text-decoration: none;
      ${ellipsisCss};
    `;
  }}
`;

const Separator = styled.div`
  margin: 0 10px 2px 11px;
  font-size: 15px;
  font-weight: 500;
`;

const CurrentInstance = styled.span`
  ${ellipsisCss};
`;

export {Container, Link, Separator, CurrentInstance};
