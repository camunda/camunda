/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field} from 'react-final-form';
import {Input, Label} from '@camunda/design-system';
import {validateTimeCharacters, validateTimeComplete, validateTimeRange} from '#/operate/shared/utils/validators';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';

type Props = {
	type: 'from' | 'to';
	labelText: string;
};

const TimeInput: React.FC<Props> = ({type, labelText}) => {
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
							onChange={(event) => {
								input.onChange(event.target.value);
							}}
							placeholder="hh:mm:ss"
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
