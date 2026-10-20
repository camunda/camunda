/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {DateRangeField} from './DateRangeField';

const MockDateRangeField: React.FC = () => {
	const [isModalOpen, setIsModalOpen] = useState(false);
	return (
		<DateRangeField
			onModalClose={() => {
				setIsModalOpen(false);
			}}
			onClick={() => {
				setIsModalOpen(true);
			}}
			isModalOpen={isModalOpen}
			popoverTitle="Filter instances by start date"
			label="Start Date Range"
			filterName="startDateRange"
			fromDateTimeKey="startDateFrom"
			toDateTimeKey="startDateTo"
		/>
	);
};

export {MockDateRangeField};
