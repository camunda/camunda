/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Content = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
`;

const FilterContainer = styled.div`
  padding: var(--cds-spacing-03) var(--cds-spacing-05) var(--cds-spacing-03) 0;
  border-bottom: 1px solid var(--cds-border-subtle-01);
`;

const ViewAllIncidentsBar = styled.div`
  margin-top: auto;
  border-top: 1px solid var(--cds-border-subtle-01);
  
  .cds--layer {
    width: 100%;
    padding: var(--cds-spacing-02) var(--cds-spacing-07) var(--cds-spacing-02)
      var(--cds-spacing-05);
    display: grid;
    grid-template-columns: 1fr 2fr auto;
    grid-gap: var(--cds-spacing-05);
    align-items: center;
  }
`;

export {Content, FilterContainer, ViewAllIncidentsBar};

