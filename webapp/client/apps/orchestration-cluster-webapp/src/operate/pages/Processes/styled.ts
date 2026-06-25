/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/type';

const SectionLabel = styled.p`
	${styles.label01};
	color: var(--cds-text-secondary);
	padding: var(--cds-spacing-05) 0 var(--cds-spacing-03);
`;

const IndentedGroup = styled.div`
	padding-left: var(--cds-spacing-07);
`;

export {SectionLabel, IndentedGroup};
