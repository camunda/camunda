/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';

const Entry = styled.li`
  ${({theme}) => {
    const colors = theme.colors.operationsPanel.skeleton.entry;

    return css`
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      border-top: ${theme.colors.borderColor};
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      min-height: 130px;
      padding: 21px 26px 28px 27px;
    `;
  }}
`;

const EntryStatus = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;

const Type = styled(BaseBlock)`
  width: 53px;
  height: 13px;
  margin-bottom: 10px;
`;
const Id = styled(BaseBlock)`
  width: 209px;
  height: 11px;
`;
const OperationIcon = styled(BaseCircle)`
  width: 19px;
  height: 19px;
  margin-top: 5px;
`;

const EntryDetails = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;

const InstancesCount = styled(BaseBlock)`
  width: 145px;
  height: 12px;
`;

export {
  Entry,
  EntryStatus,
  Type,
  Id,
  OperationIcon,
  EntryDetails,
  InstancesCount,
};
