/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const HEADER_HEIGHT = 57;

const Menu = styled.ul`
  ${({theme}) => {
    const colors = theme.colors.header;

    return css`
      display: flex;
      flex-wrap: wrap;
      font-size: 15px;
      font-weight: 500;
      color: ${colors.color};
    `;
  }}
`;

const Separator = styled.div`
  ${({theme}) => {
    const colors = theme.colors.header;

    return css`
      width: 1px;
      height: 24px;
      background-color: ${colors.separator};
      margin: 0 20px;
    `;
  }}
`;

const LeftSeparator = styled(Separator)`
  margin: 0 20px;
`;

const RightSeparator = styled(Separator)`
  margin: 0 11px 0 15px;
`;

export {HEADER_HEIGHT, Menu, LeftSeparator, RightSeparator};
