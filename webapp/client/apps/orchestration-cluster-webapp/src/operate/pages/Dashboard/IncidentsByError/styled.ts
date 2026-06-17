/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const ScrollableList = styled.div`
	flex: 1;
	overflow-y: auto;
	display: flex;
	flex-direction: column;
`;

const Row = styled.div`
	padding: var(--cds-spacing-03) 0;
	border-bottom: 1px solid var(--cds-border-subtle-01);

	&:last-child {
		border-bottom: none;
	}
`;

const LoadingRow = styled.div`
	padding: var(--cds-spacing-03) 0;
	display: flex;
	justify-content: center;
`;

export {ScrollableList, Row, LoadingRow};
