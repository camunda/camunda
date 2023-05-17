/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Stack} from '@carbon/react';
import {styles} from '@carbon/elements';

const BottomPanel = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  height: 100%;
`;

const ModificationHeader = styled.div`
  display: flex;
  align-items: center;
  padding-left: var(--cds-spacing-05);
  background-color: var(--cds-interactive);
  color: var(--cds-text-on-color);
  ${styles.bodyShort01};
`;

const ModificationFooter = styled.div`
  display: flex;
  justify-content: space-between;
  background-color: var(--cds-layer-01);
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  border-top: 1px solid var(--cds-border-subtle-01);
`;

const Buttons = styled(Stack)`
  margin-left: auto;
`;

export {BottomPanel, ModificationHeader, ModificationFooter, Buttons};
