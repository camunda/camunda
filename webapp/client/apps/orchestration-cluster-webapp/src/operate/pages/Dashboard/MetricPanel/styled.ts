/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/type';
import {Stack} from '@carbon/react';
import {Link} from '@tanstack/react-router';

const ErrorContainer = styled(Stack)`
	flex-grow: 1;
	align-items: center;
	justify-content: center;
`;

const titleStyles = css`
	&& {
		${styles.productiveHeading04};
		color: var(--cds-text-primary);
		text-decoration: none;
		display: inline-block;
		margin-bottom: var(--cds-spacing-05);
	}
`;

const Title = styled(Link)`
	${titleStyles}
	&:hover {
		text-decoration: underline;
	}
` as typeof Link;

const PendingTitle = styled.span`
	${titleStyles}
`;

const LabelContainer = styled.div`
	width: 100%;
	display: flex;
	justify-content: space-between;
`;

const Label = styled(Link)`
	&& {
		${styles.productiveHeading03};
		color: var(--cds-text-primary);
		text-decoration: none;
		&:hover {
			text-decoration: underline;
		}
	}
` as typeof Link;

export {Title, PendingTitle, LabelContainer, Label, ErrorContainer};
