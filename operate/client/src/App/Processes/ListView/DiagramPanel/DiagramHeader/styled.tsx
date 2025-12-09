/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {styles} from '@carbon/elements';
import styled from 'styled-components';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import {OPERATIONS_EXPANDED_PANEL_WIDTH} from 'modules/components/OperationsPanel/constants';

const PanelHeader = styled(BasePanelHeader)`
  padding-right: 0;
  gap: var(--cds-spacing-09);
  &.panelOffset {
    margin-right: calc(
      ${OPERATIONS_EXPANDED_PANEL_WIDTH}px - var(--cds-spacing-09)
    );
  }
`;

const Description = styled.dl`
  min-width: 5rem;
  overflow: hidden;
`;

const DescriptionTitle = styled.dt`
  ${styles.label01};
  color: var(--cds-text-secondary);
  margin-bottom: 2px;
`;

const DescriptionData = styled.dd`
  ${styles.label02};
  color: var(--cds-text-secondary);
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

const HeaderActions = styled.div`
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  margin-right: var(--cds-spacing-03);
`;

export {
  PanelHeader,
  Description,
  DescriptionTitle,
  DescriptionData,
  HeaderActions,
};
