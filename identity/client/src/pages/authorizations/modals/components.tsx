/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";

export const Row = styled.div`
  display: grid;
  width: 100%;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  grid-template-columns: 1fr 2fr;
`;

export const TextFieldContainer = styled.div`
  margin-top: 3px;
`;

export const PermissionsSectionLabel = styled.div`
  font-size: 0.75rem;
  > a {
    font-size: 0.75rem;
  }
  color: var(--cds-text-secondary);
`;
