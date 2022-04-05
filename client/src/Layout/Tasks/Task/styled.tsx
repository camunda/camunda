/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import styled from 'styled-components';
interface Props {
  isSelected: boolean;
}
const Entry = styled.li<Props>`
  display: flex;
  flex-direction: column;
  padding: 17px 20px 26px;
  min-height: 87px;
  justify-content: space-between;
  border-bottom: 1px solid ${({theme}) => theme.colors.ui05};
  background-color: ${({theme, isSelected}) =>
    isSelected ? theme.colors.link.active : theme.colors.ui04};
  cursor: pointer;
`;
const TaskInfo = styled.div`
  display: flex;
  flex-direction: column;
`;
const TaskName = styled.div`
  color: ${({theme}) => theme.colors.label01};
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 5px;
`;
const ProcessName = styled.div`
  color: ${({theme}) => theme.colors.ui06};
  font-size: 11px;
`;
const TaskStatus = styled.div`
  display: flex;
  justify-content: space-between;
`;
const Assignee = styled.div`
  color: ${({theme}) => theme.colors.ui06};
  font-size: 14px;
`;
const CreationTime = styled.div`
  color: ${({theme}) => theme.colors.ui06};
  font-size: 14px;
`;

export {
  Entry,
  TaskInfo,
  TaskName,
  ProcessName,
  TaskStatus,
  Assignee,
  CreationTime,
};
