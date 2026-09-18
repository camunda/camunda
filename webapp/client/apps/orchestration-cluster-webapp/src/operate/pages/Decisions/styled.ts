/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Link, Stack} from '@carbon/react';
import {PanelHeader as BasePanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {CopiableContent as BaseCopiableContent} from '#/operate/shared/PanelHeader/CopiableContent';

const Container = styled.section`
	height: 100%;
	display: flex;
	flex-direction: column;
`;

const DecisionName = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-04);
`;

const InstanceLink = styled(Link)`
	&& {
		text-decoration: underline;
	}
`;

const Section = styled.section`
	height: 100%;
	display: flex;
	flex-direction: column;
`;

const DecisionError = styled(Stack)`
	flex-grow: 1;
	align-items: center;
	justify-content: center;
`;

const PanelHeader = styled(BasePanelHeader)`
	padding-right: 0;
`;

const CopiableContent = styled(BaseCopiableContent)`
	display: inline-flex;
	margin-left: var(--cds-spacing-09);
	position: relative;
	&:before {
		content: ' ';
		position: absolute;
		left: calc(-1 * var(--cds-spacing-05));
		height: var(--cds-spacing-06);
		width: 1px;
		background-color: var(--cds-border-subtle-01);
	}
`;

export {Container, DecisionName, InstanceLink, Section, DecisionError, PanelHeader, CopiableContent};
