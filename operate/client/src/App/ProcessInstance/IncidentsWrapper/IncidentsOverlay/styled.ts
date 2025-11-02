/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {zNotificationContainer} from 'modules/constants/componentHierarchy';

const Overlay = styled.div`
  width: 420px;
  height: 100%;
  position: fixed;
  top: 0;
  right: 0;
  background-color: var(--cds-layer-01);
  border-left: 1px solid var(--cds-border-subtle-01);
  display: grid;
  grid-template-rows: var(--cds-spacing-08) 1fr;
  box-shadow: -4px 0 8px rgba(0, 0, 0, 0.08);
  z-index: ${zNotificationContainer - 1};
`;

export {Overlay};
