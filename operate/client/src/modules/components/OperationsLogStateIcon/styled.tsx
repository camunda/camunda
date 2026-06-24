/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {
  CheckmarkOutline as BaseCheckmarkOutline,
  ErrorOutline as BaseErrorOutline,
} from '@carbon/react/icons';

const CheckmarkOutline = styled(BaseCheckmarkOutline)`
  fill: var(--cds-support-success);
`;

const ErrorOutline = styled(BaseErrorOutline)`
  fill: var(--cds-support-error);
`;

export {CheckmarkOutline, ErrorOutline};
