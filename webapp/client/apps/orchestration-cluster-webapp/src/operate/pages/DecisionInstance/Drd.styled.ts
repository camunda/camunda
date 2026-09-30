/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack} from '@carbon/react';

const DrdContainer = styled.div`
	display: grid;
	grid-template-rows: var(--cds-spacing-09) minmax(0, 1fr);
	height: 100%;
	width: 100%;
	background: var(--cds-layer);
`;

const DrdBody = styled.div`
	position: relative;
	min-height: 0;
	overflow: hidden;
`;

const DrdControls = styled(Stack)`
	margin-left: auto;
`;

const DrdRetry = styled(Stack)`
	position: absolute;
	top: 50%;
	left: 50%;
	transform: translate(-50%, -50%);
	z-index: 1;
	padding: var(--cds-spacing-05);
	background: var(--cds-layer);
	align-items: center;
	justify-content: center;
	pointer-events: none;

	button {
		pointer-events: auto;
	}
`;

export {DrdContainer, DrdBody, DrdControls, DrdRetry};
