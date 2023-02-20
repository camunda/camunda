/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

import {ReactComponent as RetryOperation} from 'modules/components/Icon/retry.svg';
import {ReactComponent as CancelOperation} from 'modules/components/Icon/stop.svg';
import {ReactComponent as EditOperation} from 'modules/components/Icon/edit.svg';
import {ReactComponent as DeleteOperation} from 'modules/components/Icon/delete.svg';
import {ReactComponent as ModifyOperation} from 'modules/components/Icon/modify.svg';
import {styles} from '@carbon/elements';

type EntryProps = {
  isRunning?: boolean;
};

const Entry = styled.li<EntryProps>`
  ${({theme, isRunning}) => {
    const colors = theme.colors.operationsEntry.entry;

    return css`
      color: ${colors.color};
      border-top: 1px solid ${theme.colors.borderColor};
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      min-height: 130px;
      padding: 17px 27px 26px 27px;
      background-color: ${isRunning
        ? colors.isRunning.backgroundColor
        : theme.colors.ui02};
    `;
  }}
`;

const EntryStatus = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;

const Type = styled.div`
  ${styles.productiveHeading01};
  margin-bottom: 6px;
`;

const Id = styled.div`
  ${styles.label01};
`;

const EntryDetails = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;

const EndDate = styled.div`
  ${styles.label02};
  margin-left: auto;
`;

const InstancesCount = styled.div`
  ${({theme}) => {
    return css`
      ${styles.label02};
      color: ${theme.colors.linkDefault};
      text-decoration: underline;
      cursor: pointer;
    `;
  }}
`;

const OperationIcon = styled.div`
  cursor: default;
  width: 16px;
  height: 16px;
  margin-top: 10px;
`;

const iconStyle: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.operationsEntry.iconStyle;
  const opacity = theme.opacity.operationsEntry.iconStyle;

  return css`
    width: 16px;
    height: 16px;
    object-fit: contain;
    opacity: ${opacity};
    color: ${colors.color};
  `;
};

const Retry = styled(RetryOperation)`
  ${iconStyle};
`;

const Cancel = styled(CancelOperation)`
  ${iconStyle};
`;

const Edit = styled(EditOperation)`
  ${iconStyle};
`;

const Delete = styled(DeleteOperation)`
  ${iconStyle};
`;

const Modify = styled(ModifyOperation)`
  ${iconStyle};
`;

export {
  Entry,
  EntryStatus,
  Type,
  Id,
  EntryDetails,
  EndDate,
  InstancesCount,
  OperationIcon,
  Retry,
  Cancel,
  Edit,
  Delete,
  Modify,
};
