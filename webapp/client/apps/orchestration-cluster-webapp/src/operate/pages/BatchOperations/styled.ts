/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/type';
import {Link} from '@carbon/react';

const PageContainer = styled.main`
	display: flex;
	flex-direction: column;
	height: 100%;
	box-sizing: border-box;
	padding-top: var(--cds-spacing-09);
	overflow: hidden;
	background-color: var(--cds-layer);
`;

const PanelHeader = styled.div`
	padding: var(--cds-spacing-05) var(--cds-spacing-07) 0;
`;

const Title = styled.h3`
	${styles.productiveHeading04};
	margin: 0 0 var(--cds-spacing-05);
`;

const TableContainer = styled.div`
	flex: 1;
	overflow: auto;
	padding: 0 var(--cds-spacing-05);
`;

const VisuallyHiddenH1 = styled.h1`
	position: absolute;
	width: 1px;
	height: 1px;
	padding: 0;
	margin: -1px;
	overflow: hidden;
	clip: rect(0, 0, 0, 0);
	white-space: nowrap;
	border: 0;
`;

const OperationLink = styled(Link)`
	&& {
		text-decoration: underline;
	}
`;

export {PageContainer, PanelHeader, Title, TableContainer, VisuallyHiddenH1, OperationLink};
