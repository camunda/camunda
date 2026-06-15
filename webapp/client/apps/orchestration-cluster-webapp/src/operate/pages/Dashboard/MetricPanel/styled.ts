/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/type';
import {Link} from '@tanstack/react-router';

const Title = styled(Link)`
	${styles.productiveHeading04};
	color: var(--cds-text-primary) !important;
	display: inline-block;
	margin-bottom: var(--cds-spacing-05);
`;

const LabelContainer = styled.div`
	width: 100%;
	display: flex;
	justify-content: space-between;
`;

const Label = styled(Link)`
	${styles.productiveHeading03};
	color: var(--cds-text-primary) !important;
`;

export {Title, LabelContainer, Label};
