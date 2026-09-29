/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const ViewerCanvas = styled.div`
	width: 100%;
	height: 100%;
	min-height: 200px;

	.ope-selectable {
		cursor: pointer;

		&.hover .djs-outline {
			stroke: var(--cds-link-inverse);
			stroke-width: 2px;
		}
	}

	.ope-selected {
		.djs-outline {
			stroke: var(--cds-link-inverse);
			stroke-width: 2px;
		}

		.djs-visual rect {
			fill: var(--cds-highlight) !important;
		}
	}
`;

export {ViewerCanvas};
