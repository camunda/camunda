/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const FiltersContainer = styled.div`
  padding: var(--cds-spacing-05);
  border-bottom: 1px solid var(--cds-border-subtle-01);
  margin-bottom: var(--cds-spacing-05);
`;

const FilterRow = styled.div`
  display: flex;
  gap: var(--cds-spacing-04);
  align-items: end;
  margin-bottom: var(--cds-spacing-04);
  
  &:last-child {
    margin-bottom: 0;
  }
`;

const FilterGroup = styled.div`
  flex: 0 0 auto;
  min-width: 150px;
`;

export {FiltersContainer, FilterRow, FilterGroup};
