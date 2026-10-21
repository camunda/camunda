/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
	ActionableNotification,
	Button,
	InlineLoading,
	InlineNotification,
	Stack,
	TableCell,
	TableHeader,
} from '@carbon/react';
import {Add, ArrowRight, Error} from '@carbon/react/icons';
import {styles} from '@carbon/type';

const ModificationFooter = styled.div`
	display: flex;
	justify-content: space-between;
	background-color: var(--cds-layer-01);
	padding: var(--cds-spacing-03) var(--cds-spacing-05);
	border-top: 1px solid var(--cds-border-subtle-01);
`;

const Buttons = styled(Stack)`
	margin-left: auto;
`;

const Modifications = styled(Stack)`
	margin-left: var(--cds-spacing-09);
`;

const Modification = styled.p`
	display: flex;
	${styles.bodyShort01};
`;

const ModificationType = styled.span`
	${styles.bodyCompact01};
	display: flex;
	align-items: center;
	justify-content: space-between;
	min-width: var(--cds-spacing-12);
	color: var(--cds-link-primary);
`;

const iconStyle = css`
	margin-right: var(--cds-spacing-05);
	color: var(--cds-link-primary);
`;

const AddIcon = styled(Add)`
	${iconStyle}
`;

const CancelIcon = styled(Error)`
	${iconStyle}
`;

const MoveIcon = styled(ArrowRight)`
	${iconStyle}
`;

const HelperDiagram = styled.img`
	align-self: center;
`;

const DropdownAnchor = styled.span`
	position: fixed;
	pointer-events: none;
`;

const DropdownContent = styled.div`
	padding: var(--cds-spacing-05);
	color: var(--cds-text-secondary);
	pointer-events: auto;
`;

const DropdownTitle = styled.div`
	${styles.headingCompact01};
`;

const DropdownUnsupported = styled.div`
	${styles.helperText01};
	font-style: italic;
`;

const DropdownInstanceCount = styled.div`
	${styles.helperText01};
`;

const DropdownInfoMessage = styled.div`
	${styles.helperText01};
	max-width: 20rem;
	font-style: italic;
	color: var(--cds-text-secondary);
`;

const DropdownButton = styled(Button)`
	width: 100%;
`;

const DropdownLoading = styled(InlineLoading)`
	justify-content: center;
`;

const LastModificationContainer = styled.div`
	display: flex;
	align-items: center;
	height: 2rem;
	${styles.bodyShort01};
	background-color: var(--cds-button-secondary);
	color: var(--cds-text-on-color);
	padding: var(--cds-spacing-03) 0 var(--cds-spacing-03) var(--cds-spacing-05);
`;

const LastModificationDetail = styled.div`
	display: inline-flex;
	position: relative;
	padding-right: var(--cds-spacing-05);

	&::after {
		content: ' ';
		position: absolute;
		right: 0;
		height: var(--cds-spacing-05);
		width: 1px;
		background-color: var(--cds-text-on-color);
	}
`;

const SummaryTitle = styled.h4`
	${styles.productiveHeading01};
	margin-top: var(--cds-spacing-08);
	margin-bottom: var(--cds-spacing-06);
`;

const SummaryEmptyMessage = styled.p`
	padding-left: var(--cds-spacing-05);
`;

const SummaryTableContainer = styled.div`
	max-height: 185px;
	overflow-y: auto;
`;

const SummaryTableHeader = styled(TableHeader)<{$width?: string}>`
	${({$width}) => ($width === undefined ? '' : `width: ${$width};`)}
`;

const SummaryActionCell = styled(TableCell)`
	padding-top: 0 !important;
	padding-bottom: 0 !important;
`;

const SummaryEmptyCell = styled.div`
	width: 8px;
`;

const TruncatedValueContainer = styled.div`
	display: flex;
	align-items: center;
`;

const TruncatedValue = styled.div<{$hasMultipleTruncatedValue?: boolean}>`
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 1;
	overflow: hidden;
	word-break: break-all;
	${({$hasMultipleTruncatedValue}) =>
		$hasMultipleTruncatedValue ? '&:first-child { max-width: calc(50% - 6px); }' : 'max-width: 80%;'}
`;

const SummaryNotificationText = styled.p`
	margin: 0;
	${styles.bodyShort01};
`;

const SummaryNotification = styled(InlineNotification)`
	max-width: unset;
	margin-top: var(--cds-spacing-06);
`;

const SummaryActionableNotification = styled(ActionableNotification)`
	max-width: unset;
	margin-top: var(--cds-spacing-06);
`;

export {
	SummaryNotification,
	SummaryActionableNotification,
	DropdownAnchor,
	DropdownContent,
	DropdownTitle,
	DropdownUnsupported,
	DropdownInstanceCount,
	DropdownInfoMessage,
	DropdownButton,
	DropdownLoading,
	LastModificationContainer,
	LastModificationDetail,
	SummaryTitle,
	SummaryEmptyMessage,
	SummaryTableContainer,
	SummaryTableHeader,
	SummaryActionCell,
	SummaryEmptyCell,
	TruncatedValueContainer,
	TruncatedValue,
	SummaryNotificationText,
	ModificationFooter,
	Buttons,
	Modifications,
	Modification,
	ModificationType,
	AddIcon,
	CancelIcon,
	MoveIcon,
	HelperDiagram,
};
