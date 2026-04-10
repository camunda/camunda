/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {breakpoints} from '@carbon/elements';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/ErrorMessage';

const Container = styled.div`
  border-right: solid 1px var(--cds-border-subtle-01);
  background-color: var(--cds-layer-01);
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  width: 100%;
  overflow: hidden;

  @media (max-width: ${breakpoints.lg.width}) {
    border-right: none;
    border-bottom: solid 1px var(--cds-border-subtle-01);
    min-height: 250px;
    flex: 1;
  }
`;

const PanelHeader = styled(BasePanelHeader)`
  justify-content: space-between;
`;

const ErrorMessage = styled(BaseErrorMessage)`
  margin: auto;
`;

export {PanelHeader, Container, ErrorMessage};
