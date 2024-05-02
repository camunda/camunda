/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

import {
  WarningFilled as BaseWarningFilled,
  CheckmarkOutline as BaseCheckmarkOutline,
  RadioButtonChecked as BaseRadioButtonChecked,
} from '@carbon/react/icons';

const WarningFilled = styled(BaseWarningFilled)`
  fill: var(--cds-support-error);
`;

const CheckmarkOutline = styled(BaseCheckmarkOutline)`
  fill: var(--cds-icon-secondary);
`;

const RadioButtonChecked = styled(BaseRadioButtonChecked)`
  fill: var(--cds-support-success);
`;

export {WarningFilled, CheckmarkOutline, RadioButtonChecked};
