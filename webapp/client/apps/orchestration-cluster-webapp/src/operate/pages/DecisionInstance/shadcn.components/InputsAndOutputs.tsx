/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {Heading} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {useDecisionInstance} from '../decisionInstance.queries';
import {InputOutputSkeleton} from './InputOutputSkeleton';
import {InputsAndOutputsLayout} from './InputsAndOutputsLayout';
import {inputColumnWidths, outputColumnWidths} from '../inputOutputColumns';

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
					columns: [{cellContent: rule.ruleIndex}, {cellContent: output.outputName}, {cellContent: output.outputValue}],
				})),
			) ?? [],
		[matchedRules],
	);

	return (
		<div ref={containerRef} className="h-full">
			<InputsAndOutputsLayout minWidths={[width / 3, width / 3]}>
				<section
					aria-label={t('operate.decisionInstance.variablesPanel.inputVariables')}
					className="grid h-full grid-rows-[auto_1fr] overflow-hidden max-sm:h-auto max-sm:shrink-0"
				>
					{!query.isError && (
						<Heading as="h2" variant="heading-sm" className="m-0 mt-4 ml-4 text-muted-foreground">
							{t('operate.decisionInstance.variablesPanel.inputs')}
						</Heading>
					)}
					{query.isPending && <InputOutputSkeleton columnWidths={inputColumnWidths} data-testid="inputs-skeleton" />}
					{query.isError && (
						<div className="row-span-2 row-start-1 place-self-center px-4">
							<ErrorMessage className="max-w-none" />
						</div>
					)}
					{query.isSuccess && query.data.state === 'FAILED' && inputRows.length === 0 && (
						<div className="place-self-center px-4">
							<EmptyMessage className="max-w-none" message={t('operate.decisionInstance.variablesPanel.noInput')} />
						</div>
					)}
					{query.isSuccess && inputRows.length > 0 && (
						<StructuredList
							className="mx-4 mt-4 mb-4"
							label={t('operate.decisionInstance.variablesPanel.inputs')}
							cellClassName="align-middle"
							isFlush={false}
							headerColumns={[
								{cellContent: t('operate.decisionInstance.variablesPanel.name'), width: inputColumnWidths[0]},
								{cellContent: t('operate.decisionInstance.variablesPanel.value'), width: inputColumnWidths[1]},
							]}
							rows={inputRows}
						/>
					)}
				</section>
				<section
					aria-label={t('operate.decisionInstance.variablesPanel.outputVariables')}
					className="grid h-full grid-rows-[auto_1fr] overflow-hidden max-sm:h-auto max-sm:shrink-0"
				>
					{!query.isError && (
						<Heading as="h2" variant="heading-sm" className="m-0 mt-4 ml-4 text-muted-foreground">
							{t('operate.decisionInstance.variablesPanel.outputs')}
						</Heading>
					)}
					{query.isPending && <InputOutputSkeleton columnWidths={outputColumnWidths} data-testid="outputs-skeleton" />}
					{query.isError && (
						<div className="row-span-2 row-start-1 place-self-center px-4">
							<ErrorMessage className="max-w-none" />
						</div>
					)}
					{query.isSuccess && query.data.state !== 'FAILED' && (
						<StructuredList
							className="mx-4 mt-4 mb-4"
							label={t('operate.decisionInstance.variablesPanel.outputs')}
							cellClassName="align-middle"
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
						<div className="place-self-center px-4">
							<EmptyMessage className="max-w-none" message={t('operate.decisionInstance.variablesPanel.noOutput')} />
						</div>
					)}
				</section>
			</InputsAndOutputsLayout>
		</div>
	);
};

export {InputsAndOutputs};
