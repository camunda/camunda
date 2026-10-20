/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const TooltipTrigger = styled.span`
	display: inline-flex;
	cursor: not-allowed;

	& button {
		pointer-events: none;
	}
`;

const PreviewImg = styled.img`
	display: block;
	max-width: 100%;
	max-height: 80vh;
	object-fit: contain;
	margin: 0 auto;
`;

const PreviewPdf = styled.iframe`
	display: block;
	width: 100%;
	height: 80vh;
	border: none;
`;

const PreviewJSONContainer = styled.div`
	min-height: 80vh;
`;

const DocumentList = styled.ul`
	list-style: none;
	margin: 0;
	padding: var(--cds-spacing-03) 0 0;
	display: flex;
	flex-direction: column;
`;

const DocumentListItem = styled.li`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-03);
	padding: var(--cds-spacing-04) 0;
	border-bottom: 1px solid var(--cds-border-subtle-01);

	&:last-child {
		border-bottom: none;
	}
`;

const DocumentInfoBlock = styled.div`
	flex: 1;
	min-width: 0;
	display: grid;
	grid-template-columns: auto 1fr;
	grid-template-rows: auto auto;
	grid-auto-flow: column;
	column-gap: var(--cds-spacing-03);
	row-gap: var(--cds-spacing-01);
	justify-items: start;
`;

const DocumentFileName = styled.span`
	font-size: var(--cds-body-short-01-font-size);
	font-weight: var(--cds-body-short-01-font-weight);
	line-height: var(--cds-body-short-01-line-height);
	letter-spacing: var(--cds-body-short-01-letter-spacing);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
`;

const DocumentSize = styled.span`
	font-size: var(--cds-caption-01-font-size);
	font-weight: var(--cds-caption-01-font-weight);
	line-height: var(--cds-caption-01-line-height);
	letter-spacing: var(--cds-caption-01-letter-spacing);
	color: var(--cds-text-secondary);
`;

const TruncationNotice = styled.p`
	padding-inline: 0 !important;
	color: var(--cds-text-secondary);
	text-align: center;
`;

export {
	TooltipTrigger,
	PreviewImg,
	PreviewPdf,
	PreviewJSONContainer,
	DocumentList,
	DocumentListItem,
	DocumentInfoBlock,
	DocumentFileName,
	DocumentSize,
	TruncationNotice,
};
