/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Link as LinkComponent} from 'modules/components/Link';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.header.breadcrumb;

    return css`
      color: ${colors.color};
      padding: 4px 20px;
      display: flex;
      font-size: 13px;
      align-items: center;
      border-top: 1px solid ${colors.borderColor};
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
    const colors = theme.colors.header.breadcrumb;

    return css`
      color: ${colors.color};
      text-decoration: none;
      ${ellipsisCss};
    `;
  }}
`;

const Separator = styled.div`
  margin: 0 10px 1px 11px;
  font-size: 15px;
  font-weight: 500;
`;

const CurrentInstance = styled.span`
  ${ellipsisCss};
`;

export {Container, Link, Separator, CurrentInstance};
