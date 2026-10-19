/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import {percentToRadius, radiusToPercent} from '#/tasklist/modules/theme-editor/presets';

type Props = {
	label: string;
	value: string;
	onChange: (radius: string) => void;
};

const RadiusSlider: React.FC<Props> = ({label, value, onChange}) => {
	const percent = radiusToPercent(value);

	return (
		<div className="flex items-center gap-3 pb-2">
			<input
				type="range"
				aria-label={label}
				min={0}
				max={100}
				step={1}
				value={percent}
				onChange={(event) => onChange(percentToRadius(Number(event.target.value)))}
				className="min-w-0 flex-1"
				style={{accentColor: 'var(--primary-action-default)'}}
			/>
			<Text as="span" variant="label-md-strong" className="w-10 shrink-0 text-right">
				{percent}%
			</Text>
		</div>
	);
};

export {RadiusSlider};
