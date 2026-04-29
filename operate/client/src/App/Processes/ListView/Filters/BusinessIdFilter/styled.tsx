/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const FieldWithLabel = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
`;

const FilterRow = styled.div`
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: var(--cds-spacing-03);
  align-items: end;
`;

const OperatorOnlyRow = styled.div`
  display: grid;
  grid-template-columns: 140px;
`;

export {FieldWithLabel, FilterRow, OperatorOnlyRow};
