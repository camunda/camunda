/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {IconButton} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {MetricCardContainer, MetricCardTitle, MetricCardValue, MetricCardValueRow} from './agentDetails.styled';

type MetricCardProps = {
	title: string;
	value: number;
	valueInfo?: string;
	children?: React.ReactNode;
};

const MetricCard: React.FC<MetricCardProps> = ({title, value, valueInfo, children}) => {
	const id = useId();

	return (
		<MetricCardContainer aria-labelledby={id}>
			<MetricCardTitle id={id}>{title}</MetricCardTitle>
			<MetricCardValueRow>
				<MetricCardValue>{value.toLocaleString()}</MetricCardValue>
				{valueInfo !== undefined && (
					<IconButton kind="ghost" size="xs" label={valueInfo} align="bottom">
						<Information />
					</IconButton>
				)}
			</MetricCardValueRow>
			{children}
		</MetricCardContainer>
	);
};

export {MetricCard};
