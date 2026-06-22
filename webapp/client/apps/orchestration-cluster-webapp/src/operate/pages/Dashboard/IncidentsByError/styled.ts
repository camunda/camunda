/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Link} from '@tanstack/react-router';

const ScrollableList = styled.div`
	flex: 1;
	overflow-y: auto;
	display: flex;
	flex-direction: column;
`;

const LoadingRow = styled.div`
	padding: var(--cds-spacing-03) 0;
	display: flex;
	justify-content: center;
`;

const LinkWrapper = styled(Link)`
	display: block;
	text-decoration: none !important;
	padding: var(--cds-spacing-03) 0;
`;

const Li = styled.li`
	// override the hover color on expandable row's children
	&:hover {
		background-color: var(--cds-layer-hover);
	}
`;

export {ScrollableList, LoadingRow, LinkWrapper, Li};
