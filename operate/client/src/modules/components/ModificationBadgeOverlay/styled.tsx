/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {Add, Subtract} from '@carbon/react/icons';

const Modifications = styled.div`
  ${styles.label01};
  font-weight: bold;
  padding: var(--cds-spacing-02) var(--cds-spacing-04);
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 12px;
  transform: translateX(-50%);
  background-color: var(--cds-background-brand);
  color: var(--cds-text-on-color);
`;

const iconStyles = css`
  width: 18px;
  height: 18px;
  color: var(--cds-icon-on-color);
`;

const PlusIcon = styled(Add)`
  ${iconStyles}
`;

const MinusIcon = styled(Subtract)`
  ${iconStyles}
`;

export {Modifications, PlusIcon, MinusIcon};
