/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Switch, Text} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {ENVIRONMENTS, type Environment} from '#/tasklist/modules/theme-editor/presets';
import {EnvironmentBadge} from './EnvironmentBadge';
import {Section} from './Section';

type Props = {
	environment: Environment | null;
	onChange: (environment: Environment | null) => void;
};

/** Turns the header tag on or off and picks which environment it names. */
const EnvironmentField: React.FC<Props> = ({environment, onChange}) => {
	const {t} = useTranslation();

	return (
		<Section title={t('tasklist.themeEditorEnvironment')}>
			<div className="flex items-center justify-between gap-3">
				<Text as="span" variant="helper" className="text-neutral-foreground-subtle">
					{t('tasklist.themeEditorEnvironmentShow')}
				</Text>
				<Switch
					aria-label={t('tasklist.themeEditorEnvironmentShow')}
					checked={environment !== null}
					onCheckedChange={(isOn) => onChange(isOn ? 'dev' : null)}
				/>
			</div>
			{environment === null ? null : (
				<div role="radiogroup" aria-label={t('tasklist.themeEditorEnvironment')} className="flex flex-wrap gap-2">
					{ENVIRONMENTS.map((option) => (
						<button
							key={option}
							type="button"
							role="radio"
							aria-checked={option === environment}
							className={cn(
								'rounded-4xl outline-none focus-visible:ring-2 focus-visible:ring-ring',
								option === environment ? 'ring-2 ring-foreground' : 'opacity-60 hover:opacity-100',
							)}
							onClick={() => onChange(option)}
						>
							<EnvironmentBadge environment={option} />
						</button>
					))}
				</div>
			)}
		</Section>
	);
};

export {EnvironmentField};
