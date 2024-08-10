/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IconButton} from '@carbon/react';
import styled, {css} from 'styled-components';

const ButtonContainer = styled.div`
  display: flex;
  flex-direction: column;
  position: absolute;
  right: var(--cds-spacing-05);
  bottom: var(--cds-spacing-05);
`;

const buttonStyles = css`
  background-color: var(--cds-background);
`;

const ZoomResetButton = styled(IconButton)`
  ${buttonStyles}
  margin-bottom: var(--cds-spacing-02);
`;

const ZoomInButton = styled(IconButton)`
  ${buttonStyles}
`;

const ZoomOutButton = styled(IconButton)`
  ${buttonStyles}
  border-top: none;
`;

export {ButtonContainer, ZoomResetButton, ZoomInButton, ZoomOutButton};
