/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const DrdPanelContainer = styled.div`
	position: absolute;
	top: var(--cds-spacing-09);
	bottom: 0;
	right: 0;
	display: flex;
	z-index: 10;
`;

const DrdPanelSection = styled.section`
	width: 540px;
	height: 100%;
	border-left: 1px solid var(--cds-border-subtle-01);

	&.resizing {
		border-left-color: var(--cds-border-interactive);
	}
`;

const DrdPanelHandle = styled.div`
	position: absolute;
	left: -5px;
	width: 10px;
	height: 100%;
	cursor: ew-resize;

	&:focus-visible {
		outline: 2px solid var(--cds-focus);
	}
`;

export {DrdPanelContainer, DrdPanelSection, DrdPanelHandle};
