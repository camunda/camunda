/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Add, CenterCircle, Subtract, Maximize, Minimize} from '@carbon/react/icons';
// @ts-expect-error MapIdentify icon may not be in type definitions but exists in Carbon
import {MapIdentify} from '@carbon/react/icons';
import {
  ButtonContainer,
  FullscreenButton,
  ZoomResetButton,
  ZoomOutButton,
  ZoomInButton,
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
    <ButtonContainer>
      <FullscreenButton
        size="sm"
        kind="tertiary"
        align="left"
        label={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'}
        aria-label={isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen'}
        onClick={handleFullscreen}
      >
        {isFullscreen ? <Minimize /> : <Maximize />}
      </FullscreenButton>
      <MinimapButton
        size="sm"
        kind="tertiary"
        align="left"
        label={isMinimapOpen ? 'Hide minimap' : 'Show minimap'}
        aria-label={isMinimapOpen ? 'Hide minimap' : 'Show minimap'}
        onClick={handleMinimapToggle}
        isSelected={isMinimapOpen}
      >
        <MapIdentify />
      </MinimapButton>
      <ZoomResetButton
        size="sm"
        kind="tertiary"
        align="left"
        label="Reset diagram zoom"
        aria-label="Reset diagram zoom"
        onClick={handleZoomReset}
      >
        <CenterCircle />
      </ZoomResetButton>
      <ZoomInButton
        size="sm"
        kind="tertiary"
        align="left"
        label="Zoom in diagram"
        aria-label="Zoom in diagram"
        onClick={handleZoomIn}
      >
        <Add />
      </ZoomInButton>
      <ZoomOutButton
        size="sm"
        kind="tertiary"
        align="left"
        label="Zoom out diagram"
        aria-label="Zoom out diagram"
        onClick={handleZoomOut}
      >
        <Subtract />
      </ZoomOutButton>
      {processDefinitionKey === undefined ? null : (
        <DownloadBPMNDefinitionXML
          processDefinitionKey={processDefinitionKey}
        />
      )}
    </ButtonContainer>
  );
};

export default DiagramControls;
