/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {styles} from '@carbon/elements';
import styled, {css} from 'styled-components';

const FrameContainer = styled.div<{$hasBorder: boolean}>`
  height: 100%;

  ${({$hasBorder}) =>
    $hasBorder &&
    css`
      display: grid;
      grid-template-rows: var(--cds-spacing-07) 1fr;
      border: 4px solid var(--cds-interactive);
      border-top: none;
    `}
`;

const FrameHeader = styled.div`
  display: flex;
  align-items: center;
  padding-left: var(--cds-spacing-05);
  background-color: var(--cds-interactive);
  color: var(--cds-text-inverse);
  ${styles.bodyShort01};
  font-weight: bold;
`;

export {FrameContainer, FrameHeader};
