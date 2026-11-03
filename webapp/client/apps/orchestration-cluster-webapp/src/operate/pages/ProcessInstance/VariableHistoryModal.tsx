/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {Accordion, InlineLoading, InlineNotification, Modal} from '@carbon/react';
import {VariableHistoryEntry} from './VariableHistoryEntry';
import {HistoryAccordion, HistoryNotice} from './variables.styled';
import {variableHistoryQuery, type VariableHistoryTarget} from './variableHistory.queries';

type Props = {
	target: VariableHistoryTarget;
	onClose: () => void;
};

const VariableHistoryModal: React.FC<Props> = ({target, onClose}) => {
	const {data, isLoading, isError} = useQuery(variableHistoryQuery(target));
	const notTraced = (['user', 'client', 'engine'] as const)
		.filter((actor) => data !== undefined && !data.tracing[actor])
		.map((actor) => (actor === 'engine' ? 'the engine' : `${actor}s`));

	return (
		<Modal open passiveModal size="lg" modalHeading={`History of ${target.name}`} onRequestClose={onClose}>
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
		</Modal>
	);
};

export {VariableHistoryModal};
