/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isValid} from 'date-fns';
import {Field} from 'react-final-form';
import {Input, Label} from '@camunda/design-system';
import {parseDate} from '#/operate/shared/utils/parseDate';

type Props = {
	type: 'from' | 'to';
	labelText: string;
};

// Mirrors `isCompleteDate` in DateRangeModal: rejects both malformed ("2022-0") and
// calendar-invalid ("2022-02-31") dates, so a typed bad value disables Apply via
// `form.getState().invalid` instead of silently reaching the Calendar as an Invalid Date.
const validateDate = (value: string | undefined) => {
	if (!value) {
		return undefined;
	}
	if (!/^\d{4}-\d{1,2}-\d{1,2}$/.test(value) || !isValid(parseDate(value))) {
		return 'Invalid date';
	}
	return undefined;
};

const DateInput: React.FC<Props> = ({type, labelText}) => {
	return (
		<Field name={`${type}Date`} validate={validateDate}>
			{({input, meta}) => {
				const id = `${type}-date-input`;
				const hasError = Boolean(meta.error) && meta.touched;
				return (
					<div className="flex flex-col gap-1.5">
						<Label htmlFor={id}>{labelText}</Label>
						<Input
							id={id}
							value={input.value}
							onChange={(event) => {
								input.onChange(event.target.value);
							}}
							onBlur={input.onBlur}
							placeholder="YYYY-MM-DD"
							pattern="\d{4}-\d{1,2}-\d{1,2}"
							aria-invalid={hasError}
							invalidText={hasError ? meta.error : undefined}
						/>
					</div>
				);
			}}
		</Field>
	);
};

export {DateInput};
