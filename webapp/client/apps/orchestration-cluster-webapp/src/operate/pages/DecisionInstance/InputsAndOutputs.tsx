/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/ResizablePanel';
import {useDecisionInstance} from './decisionInstance.queries';
import {InputOutputSkeleton} from './InputOutputSkeleton';
import {inputColumnWidths, outputColumnWidths} from './inputOutputColumns';
import {
	ErrorMessageContainer,
	InputOutputContainer,
	InputOutputSection,
	InputOutputTable,
	Message,
	PanelEmptyMessage,
	PanelErrorMessage,
	Title,
} from './variablesPanel.styled';

type Props = {
	query: ReturnType<typeof useDecisionInstance>['query'];
};

const InputsAndOutputs: React.FC<Props> = ({query}) => {
	const {t} = useTranslation();
	const containerRef = useRef<HTMLDivElement>(null);
	const [width, setWidth] = useState(0);

	useEffect(() => {
		const container = containerRef.current;
		if (container === null) {
			return;
		}

		const observer = new ResizeObserver(() => setWidth(container.clientWidth));
		observer.observe(container);

		return () => observer.disconnect();
	}, []);

	const evaluatedInputs = query.data?.evaluatedInputs;
	const matchedRules = query.data?.matchedRules;
	const inputRows = useMemo(
		() =>
			evaluatedInputs?.map((input) => ({
				key: input.inputId,
				columns: [{cellContent: input.inputName}, {cellContent: input.inputValue}],
			})) ?? [],
		[evaluatedInputs],
	);
	const outputRows = useMemo(
		() =>
			matchedRules?.flatMap((rule, rulePosition) =>
				rule.evaluatedOutputs.map((output, outputPosition) => ({
					key: `${output.outputId}--${rulePosition}--${outputPosition}`,
					columns: [
						{cellContent: rule.ruleIndex ?? '--'},
						{cellContent: output.outputName},
						{cellContent: output.outputValue},
					],
				})),
			) ?? [],
		[matchedRules],
	);

	return (
		<InputOutputContainer ref={containerRef}>
			<ResizablePanel
				panelId="decision-instance-horizontal-panel"
				direction={SplitDirection.Horizontal}
				minWidths={[width / 3, width / 3]}
			>
				<InputOutputSection aria-label={t('operate.decisionInstance.variablesPanel.inputVariables')}>
					{!query.isError && <Title>{t('operate.decisionInstance.variablesPanel.inputs')}</Title>}
					{query.isPending && <InputOutputSkeleton columnWidths={inputColumnWidths} data-testid="inputs-skeleton" />}
					{query.isError && (
						<ErrorMessageContainer>
							<PanelErrorMessage />
						</ErrorMessageContainer>
					)}
					{query.isSuccess && query.data.state === 'FAILED' && inputRows.length === 0 && (
						<Message>
							<PanelEmptyMessage message={t('operate.decisionInstance.variablesPanel.noInput')} />
						</Message>
					)}
					{query.isSuccess && inputRows.length > 0 && (
						<InputOutputTable
							label={t('operate.decisionInstance.variablesPanel.inputs')}
							headerSize="sm"
							isFlush={false}
							headerColumns={[
								{cellContent: t('operate.decisionInstance.variablesPanel.name'), width: inputColumnWidths[0]},
								{cellContent: t('operate.decisionInstance.variablesPanel.value'), width: inputColumnWidths[1]},
							]}
							rows={inputRows}
						/>
					)}
				</InputOutputSection>
				<InputOutputSection aria-label={t('operate.decisionInstance.variablesPanel.outputVariables')}>
					{!query.isError && <Title>{t('operate.decisionInstance.variablesPanel.outputs')}</Title>}
					{query.isPending && <InputOutputSkeleton columnWidths={outputColumnWidths} data-testid="outputs-skeleton" />}
					{query.isError && (
						<ErrorMessageContainer>
							<PanelErrorMessage />
						</ErrorMessageContainer>
					)}
					{query.isSuccess && query.data.state !== 'FAILED' && (
						<InputOutputTable
							label={t('operate.decisionInstance.variablesPanel.outputs')}
							headerSize="sm"
							isFlush={false}
							headerColumns={[
								{cellContent: t('operate.decisionInstance.variablesPanel.rule'), width: outputColumnWidths[0]},
								{cellContent: t('operate.decisionInstance.variablesPanel.name'), width: outputColumnWidths[1]},
								{cellContent: t('operate.decisionInstance.variablesPanel.value'), width: outputColumnWidths[2]},
							]}
							rows={outputRows}
						/>
					)}
					{query.isSuccess && query.data.state === 'FAILED' && (
						<Message>
							<PanelEmptyMessage message={t('operate.decisionInstance.variablesPanel.noOutput')} />
						</Message>
					)}
				</InputOutputSection>
			</ResizablePanel>
		</InputOutputContainer>
	);
};

export {InputsAndOutputs};
