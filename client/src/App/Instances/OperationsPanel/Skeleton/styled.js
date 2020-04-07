/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {BaseBlock, BaseCircle} from 'modules/components/Skeleton';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Entry = themed(styled.li`
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight04
  })};

  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: 'rgba(98, 98, 110, 0.9)'
  })};

  border-top: ${themeStyle({
    dark: `solid 1px ${Colors.uiDark04}`,
    light: `solid 1px ${Colors.uiLight05}`
  })};
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 130px;
  padding: 21px 26px 28px 27px;
`);
export const EntryStatus = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;
export const Type = styled(BaseBlock)`
  width: 53px;
  height: 13px;
  margin-bottom: 10px;
`;
export const Id = styled(BaseBlock)`
  width: 209px;
  height: 11px;
`;
export const OperationIcon = styled(BaseCircle)`
  width: 19px;
  height: 19px;
  margin-top: 5px;
`;
export const EntryDetails = styled.div`
  display: flex;
  flex-direction: row;
  justify-content: space-between;
`;
export const InstancesCount = styled(BaseBlock)`
  width: 145px;
  height: 12px;
`;
