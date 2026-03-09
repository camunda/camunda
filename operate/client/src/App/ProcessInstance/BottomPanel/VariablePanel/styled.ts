/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';
import {supportError, spacing01} from '@carbon/elements';
import {AddVariableButton as BaseAddVariableButton} from '../Variables/Footer/AddVariableButton';

const EmptyMessageContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
`;

const Content = styled.div`
  position: relative;
  height: 100%;
  .cds--loading-overlay {
    position: absolute;
  }
`;

const AddVariableButton = styled(BaseAddVariableButton)`
  align-self: flex-end;
`;

const Form = styled.form`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const VariablesContainer = styled.div`
  height: 100%;
  position: relative;
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportError};
  margin-top: ${spacing01};
`;

const CountBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.25rem;
  height: 1.25rem;
  padding: 0 ${spacing01};
  border-radius: 50%;
  background-color: var(--cds-layer-accent-01);
  color: var(--cds-text-secondary);
  font-size: 0.75rem;
  font-weight: 600;
`;

export {
  Content,
  EmptyMessageContainer,
  AddVariableButton,
  Form,
  VariablesContainer,
  WarningFilled,
  CountBadge,
};
