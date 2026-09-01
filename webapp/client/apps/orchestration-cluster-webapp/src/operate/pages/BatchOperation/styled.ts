/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link, Stack, Tile as CarbonTile} from '@carbon/react';
import styled from 'styled-components';

const PageContainer = styled(Stack)`
	display: flex;
	flex-direction: column;
	height: 100%;
	box-sizing: border-box;
	padding: var(--cds-spacing-09) var(--cds-spacing-05) 0;
	background-color: var(--cds-layer);
`;

const Header = styled.div`
	display: flex;
	justify-content: space-between;
	align-items: center;
`;

const HeaderTitleContainer = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-02);
`;

const TilesContainer = styled(Stack)`
	align-self: flex-start;
`;

const Tile = styled(CarbonTile)`
	min-width: 200px;
	border: 1px solid var(--cds-border-subtle-01);
	padding: var(--cds-spacing-05);
`;

const TileLabel = styled.span`
	display: block;
	font-size: var(--cds-label-01-font-size);
	margin-bottom: var(--cds-spacing-03);
	color: var(--cds-text-weak);
`;

const TableContainer = styled.div`
	display: flex;
	flex-direction: column;
	flex: 1;
	min-height: 0;
	overflow-y: hidden;
`;

const ItemLink = styled(Link)`
	&& {
		text-decoration: underline;
	}
`;

export {PageContainer, Header, HeaderTitleContainer, TilesContainer, Tile, TileLabel, TableContainer, ItemLink};
