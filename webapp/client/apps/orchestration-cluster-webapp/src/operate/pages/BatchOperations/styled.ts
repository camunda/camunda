/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const PageContainer = styled.main`
	padding: var(--cds-spacing-05);
`;

const PanelHeader = styled.div`
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: var(--cds-spacing-05);
`;

export {PageContainer, PanelHeader};
