/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Stack} from '@carbon/react';
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

export {
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
