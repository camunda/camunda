/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Overlay = styled.div`
  width: 100%;
  height: 60%;
  position: absolute;
  background-color: var(--cds-layer-01);
  border-bottom: 1px solid var(--cds-border-subtle-01);
  display: grid;
  grid-template-rows: var(--cds-spacing-08) 1fr;
`;

export {Overlay};
