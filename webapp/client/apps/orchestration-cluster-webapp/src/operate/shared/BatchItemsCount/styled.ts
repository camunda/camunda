/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const ItemGroup = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-04);
`;

const Item = styled.span<{$color?: string}>`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-02);
	cursor: default;
	min-width: 3rem;
	color: ${({$color}) => $color ?? 'inherit'};
`;

export {ItemGroup, Item};
