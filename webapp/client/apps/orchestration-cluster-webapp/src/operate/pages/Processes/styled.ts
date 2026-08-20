/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/type';
import {Error as BaseError} from '@carbon/react/icons';
import {PanelHeader as BasePanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';

const IndentedGroup = styled.div`
	padding-left: var(--cds-spacing-06);
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

export {IndentedGroup, CanceledIcon, Section, PanelHeader, Description, DescriptionTitle, DescriptionData};
