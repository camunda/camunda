/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {TableContainer as CarbonTableContainer} from '@carbon/react';

const TableContainer = styled(CarbonTableContainer)`
	position: relative;
	background-color: var(--cds-layer);
`;

const ScrollContainer = styled.div`
	position: relative;
	height: 100%;
	background-color: var(--cds-layer);
	overflow-y: auto;
	flex: 1 0 0;
`;

const LoadingOverlay = styled.div`
	position: absolute;
	inset: 0;
	background: var(--cds-layer);
	opacity: 0.5;
	z-index: 1;
	pointer-events: none;
`;

const EmptyStateContainer = styled.div`
	display: flex;
	justify-content: center;
	align-items: center;
	padding: var(--cds-spacing-09) 0;
`;

// Used when the empty state replaces the table entirely (no header row) — fills the whole
// container instead of just padding out a table cell.
const BareEmptyStateContainer = styled.div`
	display: flex;
	justify-content: center;
	align-items: center;
	height: 100%;
`;

export {TableContainer, ScrollContainer, LoadingOverlay, EmptyStateContainer, BareEmptyStateContainer};
