/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {Panel as BasePanel} from 'modules/components/Panel';
import {StatusMessage} from 'modules/components/StatusMessage';

const Panel = styled(BasePanel)`
  ${({theme}) => {
    return css`
      border-right: solid 1px ${theme.colors.borderColor};
    `;
  }}
`;

const NodeContainer = styled.div`
  ${({theme}) => {
    return css`
      background-image: url(${theme.images.zeebraStripe});
      position: absolute;
      width: inherit;
      min-width: 100%;
      min-height: min-content;
      margin: 0;
      padding: 0;
      padding-left: 8px;
    `;
  }}
`;

const InstanceHistory = styled.div`
  ${({theme}) => {
    const colors = theme.colors.flowNodeInstanceLog;

    return css`
      position: relative;
      width: auto;
      display: flex;
      flex: 1;
      overflow: auto;
      color: ${colors.color};
    `;
  }}
`;

const InstanceHistorySkeleton = styled(InstanceHistory)`
  overflow: hidden;

  ${StatusMessage} {
    height: 58%;
  }
`;

export {Panel, NodeContainer, InstanceHistory, InstanceHistorySkeleton};
