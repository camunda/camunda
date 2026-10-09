/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Label, Switch} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {useCallback, useState} from 'react';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';

const AutoSelectNextTaskToggle: React.FC = () => {
	const {t} = useTranslation();
	const [isAutoSelectEnabled, setIsAutoSelectEnabled] = useState(
		() => getStateLocally('tasklist.autoSelectNextTask') ?? false,
	);

	const handleToggle = useCallback((checked: boolean) => {
		storeStateLocally('tasklist.autoSelectNextTask', checked);
		setIsAutoSelectEnabled(checked);
	}, []);

	return (
		<section
			className="flex h-10 items-center gap-2 border-t border-border px-4"
			aria-label={t('tasklist.taskOptionsSectionAria')}
		>
			<Switch id="toggle-auto-select-task" size="sm" checked={isAutoSelectEnabled} onCheckedChange={handleToggle} />
			<Label htmlFor="toggle-auto-select-task" className="text-sm">
				{t('tasklist.taskOptionsAutoSelectLabel')}
			</Label>
		</section>
	);
};

export {AutoSelectNextTaskToggle};
