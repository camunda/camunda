/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Dropdown as BaseDropdown, Layer, Stack as BaseStack} from '@carbon/react';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';
import {StructuredList as BaseStructuredList} from '#/operate/shared/StructuredList/StructuredList';

const Content = styled(Layer)`
	position: relative;
	height: 100%;
	display: flex;
	flex-direction: column;
	padding: 0 var(--cds-spacing-05);

	.cds--loading-overlay {
		position: absolute;
	}
`;

const StructuredList = styled(BaseStructuredList)`
	[role='table'] {
		table-layout: fixed;
	}
`;

const CellContainer = styled(BaseStack)`
	padding: var(--cds-spacing-03) var(--cds-spacing-01);
`;

const WarningFilled = styled(BaseWarningFilled)`
	fill: var(--cds-support-error);
	margin-top: var(--cds-spacing-01);
`;

const Dropdown = styled(BaseDropdown)`
	width: 200px;
	margin-bottom: var(--cds-spacing-03);
`;

const EmptyMessageWrapper = styled.div`
	display: flex;
	height: 100%;
	justify-content: center;
	align-items: center;
	text-align: center;

	> div {
		max-width: 475px;
	}
`;

const Stack = styled(Layer)`
	display: flex;
	flex-direction: column;
	flex: 1;
	min-height: 0;
`;

export {Content, StructuredList, CellContainer, WarningFilled, Dropdown, EmptyMessageWrapper, Stack};
