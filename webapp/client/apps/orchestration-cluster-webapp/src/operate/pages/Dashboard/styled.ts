/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/type';
import {Tile as BaseTile} from '@carbon/react';

const Grid = styled.div<{$numberOfColumns: 1 | 2}>`
	${({$numberOfColumns}) => css`
		width: 100%;
		height: 100%;
		padding: var(--cds-spacing-05);
		display: grid;
		grid-template-rows: 158px 1fr;
		grid-gap: var(--cds-spacing-05);
		${$numberOfColumns === 2
			? css`
					grid-template-columns: 1fr 1fr;
					& > ${Tile}:first-of-type {
						grid-column-start: 1;
						grid-column-end: 3;
					}
				`
			: css`
					grid-template-columns: 1fr;
				`}
	`}
`;

const ScrollableContent = styled.div`
	overflow-y: auto;
	display: flex;
	flex-direction: column;
	flex: 1;
`;

const Tile = styled(BaseTile)`
	display: flex;
	flex-direction: column;
	border: 1px solid var(--cds-border-subtle-01);
`;

const TileTitle = styled.h2`
	${styles.productiveHeading02};
	color: var(--cds-text-primary);
	margin-bottom: var(--cds-spacing-06);
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

export {Grid, ScrollableContent, Tile, TileTitle, VisuallyHiddenH1};
