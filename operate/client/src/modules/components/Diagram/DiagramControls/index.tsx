/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
