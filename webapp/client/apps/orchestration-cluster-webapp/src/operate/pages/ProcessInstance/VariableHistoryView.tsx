/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {Accordion, Button, InlineLoading, InlineNotification} from '@carbon/react';
import {ArrowLeft} from '@carbon/react/icons';
import {VariableHistoryEntry} from './VariableHistoryEntry';
import {HistoryAccordion, HistoryHeader, HistoryNotice, HistoryView as HistoryViewContainer} from './variables.styled';
import {variableHistoryQuery, type VariableHistoryTarget} from './variableHistory.queries';

type Props = {
	target: VariableHistoryTarget;
	onBack: () => void;
};

const VariableHistoryView: React.FC<Props> = ({target, onBack}) => {
	const {data, isLoading, isError} = useQuery(variableHistoryQuery(target));
	const notTraced = (['user', 'client', 'engine'] as const)
		.filter((actor) => data !== undefined && !data.tracing[actor])
		.map((actor) => (actor === 'engine' ? 'the engine' : `${actor}s`));

	return (
		<HistoryViewContainer>
			<HistoryHeader>
				<Button kind="ghost" size="sm" renderIcon={ArrowLeft} onClick={onBack}>
					Back to variables
				</Button>
				<h4>History of {target.name}</h4>
			</HistoryHeader>
			{notTraced.length > 0 && (
				<HistoryNotice>
					<InlineNotification
						hideCloseButton
						lowContrast
						kind="warning"
						title="History may be incomplete"
						subtitle={`Changes by ${notTraced.join(' and ')} are not recorded. A previous value is the last recorded value.`}
					/>
				</HistoryNotice>
			)}
			{isLoading && <InlineLoading description="Loading history" />}
			{isError && <InlineNotification hideCloseButton lowContrast kind="error" title="History could not be loaded." />}
			{data !== undefined && (
				<HistoryAccordion>
					<Accordion isFlush>
						{data.items.map((item) => (
							<VariableHistoryEntry key={item.variableHistoryItemKey} item={item} />
						))}
					</Accordion>
				</HistoryAccordion>
			)}
		</HistoryViewContainer>
	);
};

export {VariableHistoryView};
