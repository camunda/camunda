/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import {ErrorMessage as BaseErrorMessage} from 'modules/components/ErrorMessage';

const Container = styled.div`
  border-right: solid 1px var(--cds-border-subtle-01);
  background-color: var(--cds-layer-01);
  display: flex;
  flex-direction: column;
  min-height: 0;
`;

const PanelHeader = styled(BasePanelHeader)`
  justify-content: flex-start;
`;

const ErrorMessage = styled(BaseErrorMessage)`
  margin: auto;
`;

export {PanelHeader, Container, ErrorMessage};
