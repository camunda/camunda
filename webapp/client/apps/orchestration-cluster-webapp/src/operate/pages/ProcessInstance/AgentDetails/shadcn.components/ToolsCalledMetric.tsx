/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {LimitIndicator} from './LimitIndicator';
import {MetricCard} from './MetricCard';

type ToolsCalledMetricProps = {
	toolCalls: number;
	maxToolCalls: number;
};

const ToolsCalledMetric: React.FC<ToolsCalledMetricProps> = ({toolCalls, maxToolCalls}) => {
	const {t} = useTranslation();

	return (
		<MetricCard title={t('operate.processInstance.details.agent.toolsCalled')} value={toolCalls}>
			<LimitIndicator current={toolCalls} limit={maxToolCalls} />
			<span className="text-xs text-[var(--neutral-foreground-subtle)]">
				{t('operate.processInstance.details.agent.toolsCalledHint')}
			</span>
		</MetricCard>
	);
};

export {ToolsCalledMetric};
