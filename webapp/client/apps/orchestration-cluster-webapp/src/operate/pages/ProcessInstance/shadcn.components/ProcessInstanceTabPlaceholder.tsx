/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Text} from '@camunda/design-system';

const ProcessInstanceTabPlaceholder: React.FC = () => {
	const {t} = useTranslation();

	return (
		<Text as="p" className="p-4 text-sm text-neutral-foreground-subtle">
			{t('operate.processInstance.scaffold.bottomPanelPlaceholder')}
		</Text>
	);
};

export {ProcessInstanceTabPlaceholder};
