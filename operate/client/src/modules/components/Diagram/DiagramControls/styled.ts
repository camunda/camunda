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

const ControlsContainer = styled.div`
  display: flex;
  flex-direction: row;
  position: absolute;
  right: var(--cds-spacing-05);
  bottom: var(--cds-spacing-05);
  gap: var(--cds-spacing-03);
  align-items: center;
`;

const ButtonsGroup = styled.div`
  display: flex;
  flex-direction: row;
  gap: var(--cds-spacing-02);
  background-color: var(--cds-layer-01);
  border-radius: 2px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
`;

const DownloadGroup = styled.div`
  display: flex;
  flex-direction: row;
  background-color: var(--cds-layer-01);
  border-radius: 4px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
`;

const buttonStyles = css`
  background-color: transparent;
  border: none;
  color: inherit;
  
  &:hover {
    background-color: var(--cds-layer-hover);
    color: inherit;
  }
  
  &:active {
    background-color: var(--cds-layer-active);
    color: inherit;
  }
  
  svg {
    fill: currentColor;
  }
`;

const ControlButton = styled(IconButton)`
  ${buttonStyles}
`;

type MinimapButtonProps = {
  $isSelected?: boolean;
};

const MinimapButton = styled(IconButton)<MinimapButtonProps>`
  ${buttonStyles}
  ${({$isSelected}) =>
    $isSelected
      ? css`
          background-color: var(--cds-layer-selected);
        `
      : ''}
`;

const DownloadBPMNDefinitionXML = styled(BaseDownloadBPMNDefinitionXML)`
  ${buttonStyles}
`;

export {
  ControlsContainer,
  ButtonsGroup,
  DownloadGroup,
  ControlButton,
  MinimapButton,
  DownloadBPMNDefinitionXML,
};
