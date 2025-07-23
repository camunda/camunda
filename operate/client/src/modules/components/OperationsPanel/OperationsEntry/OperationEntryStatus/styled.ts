/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles, supportError, supportSuccess} from '@carbon/elements';
import {
  WarningFilled as BaseWarningFilled,
  CheckmarkFilled as BaseCheckmark,
} from '@carbon/react/icons';

const StatusContainer = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportError};
`;

const CheckmarkFilled = styled(BaseCheckmark)`
  fill: ${supportSuccess};
`;

const Text = styled.p`
  margin: 0;
  ${styles.bodyShort01};
`;

export {StatusContainer, WarningFilled, CheckmarkFilled, Text};
