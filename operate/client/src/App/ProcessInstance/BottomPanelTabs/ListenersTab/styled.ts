/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';
import styled from 'styled-components';
import {Stack, Dropdown as BaseDropdown, Layer} from '@carbon/react';

const Content = styled(Layer)`
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 var(--cds-spacing-05);

  [role='table'] {
    table-layout: fixed;
  }

  td {
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .cds--loading-overlay {
    position: absolute;
  }
`;

const CellContainer = styled(Stack)`
  align-items: center;
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: var(--cds-support-error);
  min-width: 1rem;
`;

const Dropdown = styled(BaseDropdown)`
  width: 200px;
  margin-bottom: var(--cds-spacing-03);
`;

export {Content, CellContainer, WarningFilled, Dropdown};
