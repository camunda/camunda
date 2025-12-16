/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IconButton} from '@carbon/react';
import styled, {css} from 'styled-components';
import {DownloadBPMNDefinitionXML as BaseDownloadBPMNDefinitionXML} from 'modules/components/DownloadBPMNDefinitionXML';

const ButtonContainer = styled.div`
  display: flex;
  flex-direction: row;
  position: absolute;
  right: var(--cds-spacing-05);
  bottom: var(--cds-spacing-05);
  gap: var(--cds-spacing-02);
`;

const buttonStyles = css`
  background-color: var(--cds-background);
`;

const FullscreenButton = styled(IconButton)`
  ${buttonStyles}
`;

const ZoomResetButton = styled(IconButton)`
  ${buttonStyles}
  margin-left: var(--cds-spacing-03);
`;

const ZoomInButton = styled(IconButton)`
  ${buttonStyles}
`;

const ZoomOutButton = styled(IconButton)`
  ${buttonStyles}
`;

const MinimapButton = styled(IconButton)<{isSelected?: boolean}>`
  ${buttonStyles}
  ${({isSelected}) =>
    isSelected
      ? `
    background-color: var(--cds-layer-selected);
  `
      : ''}
`;

const DownloadBPMNDefinitionXML = styled(BaseDownloadBPMNDefinitionXML)`
  ${buttonStyles}
  margin-left: var(--cds-spacing-03);
`;

export {
  ButtonContainer,
  FullscreenButton,
  ZoomResetButton,
  ZoomInButton,
  ZoomOutButton,
  MinimapButton,
  DownloadBPMNDefinitionXML,
};
