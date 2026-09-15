/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ComponentProps} from 'react';
import {useTranslation} from 'react-i18next';
import styled from 'styled-components';
import {InstancesList} from '#/operate/shared/InstancesList/InstancesList';
import {VisuallyHiddenH1} from '#/operate/shared/VisuallyHiddenH1/VisuallyHiddenH1';

const Container = styled.main`
	height: 100%;
`;

function ProcessesLayout(props: ComponentProps<typeof InstancesList>) {
	const {t} = useTranslation();
	return (
		<Container id="main-content" tabIndex={-1}>
			<VisuallyHiddenH1>{t('operate.processes.title')}</VisuallyHiddenH1>
			<InstancesList {...props} />
		</Container>
	);
}

export {ProcessesLayout};
