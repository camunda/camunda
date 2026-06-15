/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/type';

type Size = 'small' | 'medium' | 'large';

const getFontStyle = ({$size}: {$size: Size}) => css`
	${$size === 'small' &&
	css`
		${styles.bodyCompact01};
		color: var(--cds-text-secondary);
	`}
	${$size === 'medium' &&
	css`
		${styles.heading01};
		color: var(--cds-text-primary);
	`}
	${$size === 'large' &&
	css`
		${styles.heading02};
		color: var(--cds-text-primary);
	`}
`;

const Wrapper = styled.div<{$size: Size}>`
	${({$size}) => css`
		display: flex;
		${getFontStyle({$size})};
	`}
`;

const IncidentsCount = styled.div<{$hasIncidents?: boolean}>`
	${({$hasIncidents}) => css`
		min-width: var(--cds-spacing-09);
		color: ${$hasIncidents ? 'var(--cds-text-error)' : 'var(--cds-text-secondary)'};
	`}
`;

const ActiveCount = styled.div<{$hasActiveInstances?: boolean}>`
	${({$hasActiveInstances}) => css`
		margin-left: auto;
		width: 139px;
		text-align: right;
		color: ${$hasActiveInstances ? 'var(--cds-tag-color-green)' : 'var(--cds-text-primary)'};
	`}
`;

const Label = styled.div<{$isRed?: boolean; $size?: 'small' | 'medium'}>`
	${({$size, $isRed}) => css`
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		${styles.bodyCompact01}
		color: var(--cds-text-secondary);
		${$size === 'medium' &&
		css`
			${styles.headingCompact01}
			color: var(--cds-text-primary);
		`}
		${$isRed &&
		css`
			color: var(--cds-text-error);
		`}
	`}
`;

const BarContainer = styled.div`
	position: relative;
	margin: var(--cds-spacing-03) 0;
`;

const getBarHeight = ($size: Size) => css`
	${$size === 'small' &&
	css`
		height: var(--cds-spacing-01);
	`}
	${$size === 'medium' &&
	css`
		height: var(--cds-spacing-02);
	`}
	${$size === 'large' &&
	css`
		height: var(--cds-spacing-03);
	`}
`;

const ActiveInstancesBar = styled.div<{$isPassive?: boolean; $size: Size}>`
	${({$isPassive, $size}) => css`
		${getBarHeight($size)};
		background: ${$isPassive ? 'var(--cds-border-subtle-01)' : 'var(--cds-support-success)'};
	`}
`;

const IncidentsBar = styled.div<{$size: Size}>`
	${({$size}) => css`
		${getBarHeight($size)};
		position: absolute;
		top: 0;
		background: var(--cds-support-error);
	`}
`;

export {Wrapper, IncidentsCount, ActiveCount, Label, BarContainer, ActiveInstancesBar, IncidentsBar};
