/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import SplitPane from 'modules/components/SplitPane';

const PaneBody = styled(SplitPane.Pane.Body)`
  border-top: none;
`;

const Title = styled.span`
  padding-right: 34px;
`;

const InstancesCount = styled.span`
  ${({theme}) => {
    const colors = theme.colors.list.header.title;

    return css`
      border-left: 1px solid ${colors.borderColor};
      padding-left: 30px;
      font-size: 14px;
      font-weight: 500;
    `;
  }}
`;

export {PaneBody, Title, InstancesCount};
