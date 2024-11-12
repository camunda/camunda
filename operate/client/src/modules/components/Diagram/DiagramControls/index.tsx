/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Add, CenterCircle, Subtract} from '@carbon/react/icons';
import {
  ButtonContainer,
  ZoomResetButton,
  ZoomOutButton,
  ZoomInButton,
} from './styled';

type Props = {
  handleZoomReset: () => void;
  handleZoomIn: () => void;
  handleZoomOut: () => void;
};

const DiagramControls: React.FC<Props> = ({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut,
}) => {
  return (
    <ButtonContainer>
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
    </ButtonContainer>
  );
};

export default DiagramControls;
