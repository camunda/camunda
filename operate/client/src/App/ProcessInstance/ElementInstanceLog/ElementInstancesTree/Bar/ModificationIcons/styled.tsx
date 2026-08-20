/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {WarningAltFilled, Error, Add} from '@carbon/react/icons';

const Container = styled.div`
  display: flex;
  align-items: center;
  justify-self: flex-end;
`;

const iconStyles = css`
  color: var(--cds-icon-disabled);
`;

const AddIcon = styled(Add)`
  ${iconStyles}
`;

const CancelIcon = styled(Error)`
  ${iconStyles}
`;

const WarningIcon = styled(WarningAltFilled)`
  color: var(--cds-support-warning);
`;

export {Container, AddIcon, CancelIcon, WarningIcon};
