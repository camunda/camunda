/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {FieldsModal} from './FieldsModal';
import {FilterNameModal} from './FilterNameModal';
import {DeleteFilterModal} from './DeleteFilterModal';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import type {NamedCustomFilters} from '#/tasklist/modules/available-tasks/customFiltersSchema';

const DEFAULT_FILTER_VALUES: NamedCustomFilters = {
	assignee: 'all',
	status: 'all',
};

function getCustomFilterParams(filterId: string | undefined): NamedCustomFilters {
	const storedFilters = getStateLocally('tasklist.customFilters');

	if (filterId === undefined || storedFilters === null) {
		return DEFAULT_FILTER_VALUES;
	}

	return storedFilters[filterId] ?? DEFAULT_FILTER_VALUES;
}

type Props = Omit<React.ComponentProps<typeof FieldsModal>, 'initialValues' | 'onSave' | 'onApply' | 'onEdit'> & {
	filterId?: string;
	onSuccess: (filterId: string) => void;
	onEditSuccess?: (filterId: string) => void;
};

const CustomFiltersModal: React.FC<Props> = ({filterId, isOpen, onSuccess, onDelete, onEditSuccess, ...props}) => {
	const [customFilters, setCustomFilters] = useState(getCustomFilterParams(filterId));
	const initialCustomFilters = useMemo(() => getCustomFilterParams(filterId), [filterId]);
	const [currentStep, setCurrentStep] = useState<'fields' | 'name' | 'delete'>('fields');

	return (
		<>
			<FieldsModal
				isOpen={isOpen && currentStep === 'fields'}
				initialValues={initialCustomFilters}
				onApply={(filters) => {
					storeStateLocally('tasklist.customFilters', {
						...getStateLocally('tasklist.customFilters'),
						custom: filters,
					});
					onSuccess('custom');
				}}
				onSave={(filters) => {
					setCustomFilters(filters);
					setCurrentStep('name');
				}}
				onEdit={(filters) => {
					setCurrentStep('fields');

					if (filterId === undefined) {
						console.error('Filter ID is undefined on edit');
						props.onClose();

						return;
					}

					storeStateLocally('tasklist.customFilters', {
						...getStateLocally('tasklist.customFilters'),
						[filterId]: {
							...filters,
						},
					});

					onSuccess(filterId);
					onEditSuccess?.(filterId);
				}}
				onDelete={() => {
					setCurrentStep('delete');
				}}
				{...props}
			/>
			<FilterNameModal
				isOpen={isOpen && currentStep === 'name'}
				onApply={(filterName) => {
					const newFilterId = `${Date.now()}${Math.random()}`;
					storeStateLocally('tasklist.customFilters', {
						...getStateLocally('tasklist.customFilters'),
						[newFilterId]: {
							...customFilters,
							name: filterName,
						},
					});
					setCurrentStep('fields');

					onSuccess(newFilterId);
				}}
				onCancel={() => setCurrentStep('fields')}
			/>
			<DeleteFilterModal
				isOpen={isOpen && currentStep === 'delete'}
				filterId={filterId ?? ''}
				onClose={() => setCurrentStep('fields')}
				onDelete={() => {
					const stored = getStateLocally('tasklist.customFilters') ?? {};
					storeStateLocally(
						'tasklist.customFilters',
						Object.fromEntries(Object.entries(stored).filter(([name]) => name !== filterId)),
					);
					setCurrentStep('fields');
					onDelete(filterId!);
				}}
			/>
		</>
	);
};

export {CustomFiltersModal};
