/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Input, Label} from '@camunda/design-system';
import {validateTimeCharacters, validateTimeComplete, validateTimeRange} from '#/operate/shared/utils/validators';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';

type Props = {
	type: 'from' | 'to';
	labelText: string;
};

const SEGMENT_MAX = [23, 59, 59];

function stepTimeSegment(value: string, caret: number, delta: 1 | -1) {
	const parts = value.split(':');
	const base = parts.length === 3 && parts.every((part) => /^\d{1,2}$/.test(part)) ? parts.map(Number) : [0, 0, 0];
	const segment = Math.min(Math.floor(caret / 3), 2);
	const size = SEGMENT_MAX[segment] + 1;
	base[segment] = (base[segment] + delta + size) % size;
	return {value: base.map((part) => String(part).padStart(2, '0')).join(':'), segment};
}

const TimeInput: React.FC<Props> = ({type, labelText}) => {
	const {t} = useTranslation();
	return (
		<Field
			name={`${type}Time`}
			validate={mergeValidators(validateTimeComplete, validateTimeCharacters, validateTimeRange)}
		>
			{({input, meta}) => {
				const id = `${type}-time-picker`;
				return (
					<div className="flex flex-1 flex-col gap-1.5">
						<Label htmlFor={id}>{labelText}</Label>
						<Input
							value={input.value}
							id={id}
							onKeyDown={(event) => {
								if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') {
									return;
								}
								event.preventDefault();
								const target = event.currentTarget;
								const next = stepTimeSegment(input.value, target.selectionStart ?? 0, event.key === 'ArrowUp' ? 1 : -1);
								input.onChange(next.value);
								requestAnimationFrame(() => target.setSelectionRange(next.segment * 3, next.segment * 3 + 2));
							}}
							onChange={(event) => {
								input.onChange(event.target.value);
							}}
							placeholder={t('operate.shared.dateRangeField.timePlaceholder')}
							data-testid={`${type}Time`}
							maxLength={8}
							autoComplete="off"
							aria-invalid={meta.invalid}
							invalidText={meta.error}
						/>
					</div>
				);
			}}
		</Field>
	);
};

export {TimeInput};
