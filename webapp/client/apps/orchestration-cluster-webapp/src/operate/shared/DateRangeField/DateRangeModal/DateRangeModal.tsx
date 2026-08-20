/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form} from 'react-final-form';
import {DatePicker, Layer, Modal, Stack} from '@carbon/react';
import {createPortal} from 'react-dom';
import {logger} from '#/operate/shared/utils/logger';
import {formatDate} from '../formatDate';
import {DateInput} from './DateInput';
import {TimeInput} from './TimeInput';
import {TimeInputStack} from './styled';

const defaultTime = {
	from: '00:00:00',
	to: '23:59:59',
};

// Only complete dates may reach the DatePicker: while the user is typing,
// partial values ("2022-0") would be parsed by flatpickr, formatted as
// garbage back into the input, and crash its range plugin on blur.
const isCompleteDate = (date: string | undefined): date is string => /^\d{4}-\d{1,2}-\d{1,2}$/.test(date ?? '');

type Props = {
	title: string;
	onCancel: () => void;
	onApply: ({fromDateTime, toDateTime}: {fromDateTime: Date; toDateTime: Date}) => void;
	defaultValues: {
		fromDate: string;
		fromTime: string;
		toDate: string;
		toTime: string;
	};
	isModalOpen: boolean;
};

const DateRangeModal: React.FC<Props> = ({defaultValues, onApply, onCancel, title, isModalOpen}) => {
	const handleApply = ({
		fromDate,
		fromTime,
		toDate,
		toTime,
	}: {
		fromDate?: string;
		fromTime?: string;
		toDate?: string;
		toTime?: string;
	}) => {
		if (fromDate !== undefined && fromTime !== undefined && toDate !== undefined && toTime !== undefined) {
			try {
				onApply({
					fromDateTime: new Date(`${fromDate} ${fromTime}`),
					toDateTime: new Date(`${toDate} ${toTime}`),
				});
			} catch (e) {
				logger.error(e);
			}
		}
	};

	return (
		<Form onSubmit={handleApply} initialValues={defaultValues}>
			{({handleSubmit, form, values}) => (
				<Layer level={0}>
					<>
						{createPortal(
							<Modal
								data-testid="date-range-modal"
								open={isModalOpen}
								size="xs"
								modalHeading={title}
								primaryButtonText="Apply"
								secondaryButtonText="Cancel"
								onRequestClose={onCancel}
								onRequestSubmit={handleSubmit}
								primaryButtonDisabled={
									!form.getFieldState('fromDate')?.value ||
									!form.getFieldState('fromTime')?.value ||
									!form.getFieldState('toDate')?.value ||
									!form.getFieldState('toTime')?.value ||
									form.getState().invalid
								}
							>
								<Stack gap={6}>
									<div>
										<DatePicker
											datePickerType="range"
											// Carbon's `DatePickerInput` explicitly doesn't support a `value` prop on
											// itself for range pickers; the parent `DatePicker` is the supported,
											// documented way to control the selected dates.
											value={[values.fromDate, values.toDate].filter(isCompleteDate)}
											onChange={(event) => {
												const [fromDateTime, toDateTime] = event;
												if (fromDateTime !== undefined) {
													form.change('fromDate', formatDate(fromDateTime));
													if (form.getFieldState('fromTime')?.value === '') {
														form.change('fromTime', defaultTime.from);
													}
												}
												if (toDateTime !== undefined) {
													form.change('toDate', formatDate(toDateTime));
													if (form.getFieldState('toTime')?.value === '') {
														form.change('toTime', defaultTime.to);
													}
												}
											}}
											dateFormat="Y-m-d"
											short
										>
											<DateInput id="date-picker-input-id-start" type="from" labelText="From date" />
											<DateInput id="date-picker-input-id-finish" type="to" labelText="To date" />
										</DatePicker>
									</div>
									<TimeInputStack orientation="horizontal">
										<TimeInput type="from" labelText="From time" />
										<TimeInput type="to" labelText="To time" />
									</TimeInputStack>
								</Stack>
							</Modal>,
							document.body,
						)}
					</>
				</Layer>
			)}
		</Form>
	);
};

export {DateRangeModal};
