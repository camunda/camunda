/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Add, CenterCircle, Subtract, Maximize, Minimize, Plan} from '@carbon/react/icons';
import {
  ControlsContainer,
  ButtonsGroup,
  DownloadGroup,
  ControlButton,
  MinimapButton,
  DownloadBPMNDefinitionXML,
} from './styled';

type Props = {
  handleZoomReset: () => void;
  handleZoomIn: () => void;
  handleZoomOut: () => void;
  handleFullscreen: () => void;
  isFullscreen: boolean;
  handleMinimapToggle: () => void;
  isMinimapOpen: boolean;
  processDefinitionKey?: string;
};

const DiagramControls: React.FC<Props> = ({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut,
  handleFullscreen,
  isFullscreen,
  handleMinimapToggle,
  isMinimapOpen,
  processDefinitionKey,
}) => {
  return (
    <ControlsContainer>
      <ButtonsGroup>
        <ControlButton
          size="sm"
          kind="tertiary"
          align="top"
          label={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'}
          aria-label={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'}
          onClick={handleFullscreen}
        >
          {isFullscreen ? <Minimize /> : <Maximize />}
        </ControlButton>
        <MinimapButton
          size="sm"
          kind="tertiary"
          align="top"
          label={isMinimapOpen ? 'Hide minimap' : 'Show minimap'}
          aria-label={isMinimapOpen ? 'Hide minimap' : 'Show minimap'}
          onClick={handleMinimapToggle}
          $isSelected={isMinimapOpen}
        >
          <Plan />
        </MinimapButton>
        <ControlButton
          size="sm"
          kind="tertiary"
          align="top"
          label="Reset zoom"
          aria-label="Reset zoom"
          onClick={handleZoomReset}
        >
          <CenterCircle />
        </ControlButton>
        <ControlButton
          size="sm"
          kind="tertiary"
          align="top"
          label="Zoom out"
          aria-label="Zoom out"
          onClick={handleZoomOut}
        >
          <Subtract />
        </ControlButton>
        <ControlButton
          size="sm"
          kind="tertiary"
          align="top"
          label="Zoom in"
          aria-label="Zoom in"
          onClick={handleZoomIn}
        >
          <Add />
        </ControlButton>
      </ButtonsGroup>
      {processDefinitionKey !== undefined && (
        <DownloadGroup>
          <DownloadBPMNDefinitionXML
            processDefinitionKey={processDefinitionKey}
          />
        </DownloadGroup>
      )}
    </ControlsContainer>
  );
};

export default DiagramControls;
