/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/type';
import {Button, InlineNotification, Link, Stack, TableHeader} from '@carbon/react';
import {createLink} from '@tanstack/react-router';
import {Add, Error as BaseError, Subtract} from '@carbon/react/icons';
import {PanelHeader as BasePanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';

const IndentedGroup = styled.div`
	padding-left: var(--cds-spacing-06);
`;

const TenantFilterGroup = styled.fieldset`
	border: 0;
	padding: 0;
	margin: 0;
`;

const CanceledIcon = styled(BaseError)`
	flex-shrink: 0;
	fill: var(--cds-icon-secondary);
`;

const Section = styled.section`
	display: flex;
	flex-direction: column;
	height: 100%;
`;

const PanelHeader = styled(BasePanelHeader)`
	padding-right: 0;
	gap: var(--cds-spacing-09);
`;

const Description = styled.dl`
	min-width: 5rem;
	overflow: hidden;
`;

const DescriptionTitle = styled.dt`
	${styles.label01};
	color: var(--cds-text-secondary);
	margin-bottom: 2px;
`;

const DescriptionData = styled.dd`
	${styles.label02};
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-02);
	color: var(--cds-text-secondary);
	text-overflow: ellipsis;
	overflow: hidden;
	white-space: nowrap;
`;

const InstancesTableContainer = styled.section`
	height: 100%;
	display: flex;
	flex-direction: column;
`;

const BatchModificationActions = styled(Stack)`
	background-color: var(--cds-layer);
	width: 100%;
	justify-content: flex-end;
	padding: var(--cds-spacing-03) var(--cds-spacing-05);
	border-top: 1px solid var(--cds-border-subtle-01);
`;

const SummaryTitle = styled.h3`
	${styles.productiveHeading01};
	margin-top: var(--cds-spacing-08);
	margin-bottom: var(--cds-spacing-06);
`;

const SummaryTableHeader = styled(TableHeader)<{$width: string}>`
	width: ${({$width}) => $width};
`;

const BatchModificationNotificationContainer = styled.div`
	position: relative;
`;

const BatchModificationInlineNotification = styled(InlineNotification)`
	min-block-size: 32px;
	max-block-size: 32px;
	max-inline-size: unset;

	.cds--inline-notification__icon {
		margin-block-start: unset;
	}

	.cds--inline-notification__text-wrapper {
		padding: unset;
	}

	.cds--inline-notification__details {
		align-items: center;
	}
`;

const UndoButton: typeof Button = styled(Button)`
	position: absolute;
	top: 0;
	right: 0;
`;

const Modifications = styled.div`
	${styles.label01};
	font-weight: bold;
	padding: var(--cds-spacing-02) var(--cds-spacing-04);
	display: flex;
	justify-content: center;
	align-items: center;
	border-radius: 12px;
	transform: translateX(-50%);
	background-color: var(--cds-background-brand);
	color: var(--cds-text-on-color);
`;

const modificationIconStyles = css`
	width: 18px;
	height: 18px;
	color: var(--cds-icon-on-color);
`;

const PlusIcon = styled(Add)`
	${modificationIconStyles}
`;

const MinusIcon = styled(Subtract)`
	${modificationIconStyles}
`;

const ProcessName = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-04);
`;

const InstanceLink = createLink<React.FC<React.ComponentProps<'a'>>>(styled(Link)`
	&& {
		text-decoration: underline;
	}
`);

const VisuallyHiddenStatus = styled.span`
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

export {
	IndentedGroup,
	TenantFilterGroup,
	CanceledIcon,
	Section,
	PanelHeader,
	Description,
	DescriptionTitle,
	DescriptionData,
	InstancesTableContainer,
	BatchModificationActions,
	SummaryTitle,
	SummaryTableHeader,
	BatchModificationNotificationContainer,
	BatchModificationInlineNotification,
	UndoButton,
	Modifications,
	PlusIcon,
	MinusIcon,
	ProcessName,
	InstanceLink,
	VisuallyHiddenStatus,
};
