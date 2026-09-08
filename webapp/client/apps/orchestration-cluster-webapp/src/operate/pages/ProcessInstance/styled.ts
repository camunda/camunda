/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Link, Breadcrumb} from '@carbon/react';
import {createLink} from '@tanstack/react-router';

const ProcessInstanceLink = createLink(Link);
const CarbonBreadcrumb = styled(Breadcrumb)`
	display: flex;
	align-items: center;
	background-color: var(--cds-layer-01);
	border-bottom: 1px solid var(--cds-border-subtle-01);
	padding-left: var(--cds-spacing-05);
`;

const Container = styled.main.attrs({id: 'main-content', tabIndex: -1})`
	height: 100%;
	position: relative;
	padding-top: var(--cds-spacing-09);
`;

export {Container, ProcessInstanceLink, CarbonBreadcrumb};
