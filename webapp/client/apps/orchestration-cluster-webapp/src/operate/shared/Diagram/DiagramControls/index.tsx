/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Add, CenterCircle, Subtract, Maximize, Minimize, Plan} from '@carbon/react/icons';
import {ControlsContainer, ButtonsGroup, ControlButton, MinimapButton} from './styled';

type Props = {
	handleZoomReset: () => void;
	handleZoomIn: () => void;
	handleZoomOut: () => void;
	handleFullscreen: () => void;
	isFullscreen: boolean;
	handleMinimapToggle: () => void;
	isMinimapOpen: boolean;
};

function DiagramControls({
	handleZoomReset,
	handleZoomIn,
	handleZoomOut,
	handleFullscreen,
	isFullscreen,
	handleMinimapToggle,
	isMinimapOpen,
}: Props) {
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
					label="Reset diagram zoom"
					aria-label="Reset diagram zoom"
					onClick={handleZoomReset}
				>
					<CenterCircle />
				</ControlButton>
				<ControlButton
					size="sm"
					kind="tertiary"
					align="top"
					label="Zoom out diagram"
					aria-label="Zoom out diagram"
					onClick={handleZoomOut}
				>
					<Subtract />
				</ControlButton>
				<ControlButton
					size="sm"
					kind="tertiary"
					align="top"
					label="Zoom in diagram"
					aria-label="Zoom in diagram"
					onClick={handleZoomIn}
				>
					<Add />
				</ControlButton>
			</ButtonsGroup>
		</ControlsContainer>
	);
}

export {DiagramControls};
