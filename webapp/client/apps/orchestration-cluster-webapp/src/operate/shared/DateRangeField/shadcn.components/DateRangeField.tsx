/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Field, useField, useForm} from 'react-final-form';
import {Calendar} from '@camunda/design-system/icons';
import {IconTextInput} from '#/operate/shared/IconInput/shadcn.components/IconTextInput';
import {formatDate, formatISODate, formatTime} from '#/operate/shared/DateRangeField/formatDate';
import {DateRangeModal} from './DateRangeModal/DateRangeModal';

type Props = {
	filterName: string;
	popoverTitle: string;
	label: string;
	fromDateTimeKey: string;
	toDateTimeKey: string;
	isModalOpen: boolean;
	onModalClose: () => void;
	onClick: () => void;
};

const formatInputValue = (fromDateTime?: Date, toDateTime?: Date) => {
	if (fromDateTime === undefined || toDateTime === undefined) {
		return '';
	}
	return `${formatDate(fromDateTime)} ${formatTime(fromDateTime)} - ${formatDate(toDateTime)} ${formatTime(toDateTime)}`;
};

const DateRangeField: React.FC<Props> = ({
	filterName,
	popoverTitle,
	label,
	fromDateTimeKey,
	toDateTimeKey,
	isModalOpen,
	onModalClose,
	onClick,
}) => {
	const {t} = useTranslation();
	const form = useForm();
	const fromDateTime = useField<string>(fromDateTimeKey).input.value;
	const toDateTime = useField<string>(toDateTimeKey).input.value;

	const getInputValue = () => {
		if (isModalOpen) {
			return t('operate.shared.dateRangeField.custom');
		}
		if (fromDateTime !== '' && toDateTime !== '') {
			return formatInputValue(new Date(fromDateTime), new Date(toDateTime));
		}
		return '';
	};

	const handleClick = () => {
		if (!isModalOpen) {
			onClick();
		}
	};

	return (
		<>
			<div>
				<IconTextInput
					Icon={Calendar}
					id={`optional-filter-${filterName}`}
					labelText={label}
					value={getInputValue()}
					title={getInputValue()}
					placeholder={t('operate.shared.dateRangeField.enterDateRange')}
					buttonLabel={t('operate.shared.dateRangeField.openModal')}
					onIconClick={handleClick}
					onClick={handleClick}
				/>
				{[fromDateTimeKey, toDateTimeKey].map((filterKey) => (
					<Field name={filterKey} key={filterKey} component="input" type="hidden" />
				))}
			</div>

			{isModalOpen ? (
				<DateRangeModal
					isModalOpen={isModalOpen}
					title={popoverTitle}
					onCancel={onModalClose}
					onApply={({fromDateTime, toDateTime}) => {
						onModalClose();
						form.change(fromDateTimeKey, formatISODate(fromDateTime));
						form.change(toDateTimeKey, formatISODate(toDateTime));
					}}
					defaultValues={{
						fromDate: fromDateTime === '' ? '' : formatDate(new Date(fromDateTime)),
						fromTime: fromDateTime === '' ? '' : formatTime(new Date(fromDateTime)),
						toDate: toDateTime === '' ? '' : formatDate(new Date(toDateTime)),
						toTime: toDateTime === '' ? '' : formatTime(new Date(toDateTime)),
					}}
					key={`date-range-modal-${isModalOpen ? 'open' : 'closed'}`}
				/>
			) : null}
		</>
	);
};

export {DateRangeField};
