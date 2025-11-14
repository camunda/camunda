/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Container = styled.div`
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 var(--cds-spacing-05) var(--cds-spacing-05);
`;

const TableContainer = styled.div`
  flex: 1;
  overflow: auto;
`;

const ActionCell = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
`;

export {Container, TableContainer, ActionCell};
