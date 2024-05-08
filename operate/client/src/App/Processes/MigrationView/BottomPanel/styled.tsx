/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
