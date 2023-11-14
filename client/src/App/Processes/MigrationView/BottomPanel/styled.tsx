/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {DataTable as BaseDataTable} from 'modules/components/DataTable';

const BottomSection = styled.section`
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--cds-layer);
  overflow: auto;
`;

const LeftColumn = styled.div`
  display: flex;
  justify-content: space-between;
  color: var(--cds-text-primary);
`;

const DataTable = styled(BaseDataTable)`
  td {
    padding-top: 0;
    padding-bottom: 0;
  }
`;

const ErrorMessageContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
`;

export {BottomSection, LeftColumn, DataTable, ErrorMessageContainer};
