/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {
  TableContainer as BaseTableContainer,
  TableCell as BaseTableCell,
  TableHead as BaseTableHead,
} from '@carbon/react';

const Container = styled.div`
  height: 100%;
  overflow-y: auto;
  flex: 1 0 0;
`;

const TableContainer = styled(BaseTableContainer)`
  padding-top: 0;
`;

const TableCell = styled(BaseTableCell)`
  white-space: nowrap;
`;

const TableHead = styled(BaseTableHead)`
  white-space: nowrap;
`;

const EmptyMessageContainer = styled.div`
  display: flex;
  justify-content: center;
  height: 100%;
  align-items: center;
  background-color: var(--cds-layer-01);
`;

export {Container, TableContainer, TableCell, TableHead, EmptyMessageContainer};
