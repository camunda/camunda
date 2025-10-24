/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import {CopiableContent as BaseCopiableContent} from 'modules/components/PanelHeader/CopiableContent';
import {OPERATIONS_EXPANDED_PANEL_WIDTH} from 'modules/components/OperationsPanel/constants';

const PanelHeader = styled(BasePanelHeader)`
  padding-right: 0;
  &.panelOffset {
    margin-right: calc(
      ${OPERATIONS_EXPANDED_PANEL_WIDTH}px - var(--cds-spacing-09)
    );
  }
`;

const Section = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const CopiableContent = styled(BaseCopiableContent)`
  display: inline-flex;
  margin-left: var(--cds-spacing-09);
  position: relative;
  &:before {
    content: ' ';
    position: absolute;
    left: calc(-1 * var(--cds-spacing-05));
    height: var(--cds-spacing-06);
    width: 1px;
    background-color: var(--cds-border-subtle-01);
  }
`;

export {PanelHeader, Section, CopiableContent};
