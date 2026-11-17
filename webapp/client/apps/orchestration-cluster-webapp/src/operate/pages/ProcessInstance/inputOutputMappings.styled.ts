/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Callout as BaseCallout} from '@carbon/react';
import {EmptyMessage as BaseEmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';

const Content = styled.div<{$isInfoBannerVisible: boolean}>`
	height: 100%;
	padding-inline: var(--cds-spacing-05);
	overflow: hidden;
	display: grid;
	grid-template-rows: ${({$isInfoBannerVisible}) => ($isInfoBannerVisible ? 'auto 1fr' : '1fr')};
	${({$isInfoBannerVisible}) =>
		$isInfoBannerVisible &&
		css`
			grid-gap: var(--cds-spacing-05);
		`}
`;

const EmptyMessage = styled(BaseEmptyMessage)`
	align-self: center;
	margin: auto;
`;

const Callout = styled(BaseCallout)`
	min-width: 100%;
`;

export {Content, EmptyMessage, Callout};
