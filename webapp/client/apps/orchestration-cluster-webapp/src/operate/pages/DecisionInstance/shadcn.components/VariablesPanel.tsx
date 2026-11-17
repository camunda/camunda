/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Heading, Tabs, TabsContent, TabsList, TabsTrigger} from '@camunda/design-system';
import {LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {PanelHeader} from '#/operate/shared/PanelHeader/shadcn.components/PanelHeader';
import {useDecisionInstance} from '../decisionInstance.queries';
import {inputColumnWidths, outputColumnWidths} from '../inputOutputColumns';
import {InputsAndOutputsLayout} from './InputsAndOutputsLayout';
import {InputOutputSkeleton} from './InputOutputSkeleton';
import {InputsAndOutputs} from './InputsAndOutputs';
import {Result} from './Result';

type Props = {
	decisionEvaluationInstanceKey: string;
};

type TabsProps = {
	inputsAndOutputs: React.ReactNode;
	result: React.ReactNode;
};

const INPUTS_AND_OUTPUTS_TAB = 'inputs-and-outputs';
const RESULT_TAB = 'result';

const VariablesPanelTabs: React.FC<TabsProps> = ({inputsAndOutputs, result}) => {
	const {t} = useTranslation();
	const [hasViewedResult, setHasViewedResult] = useState(false);

	return (
		<div data-testid="decision-instance-variables-panel" className="flex h-full w-full flex-col overflow-hidden">
			<Tabs
				defaultValue={INPUTS_AND_OUTPUTS_TAB}
				onValueChange={(value) => {
					if (value === RESULT_TAB) {
						setHasViewedResult(true);
					}
				}}
				className="flex min-h-0 grow flex-col gap-0"
			>
				<div className="flex h-12 min-h-12 items-center border-b border-border px-4">
					<TabsList aria-label={t('operate.decisionInstance.ds.variablesPanel.tabsLabel')}>
						<TabsTrigger value={INPUTS_AND_OUTPUTS_TAB}>
							{t('operate.decisionInstance.ds.variablesPanel.inputsAndOutputs')}
						</TabsTrigger>
						<TabsTrigger value={RESULT_TAB}>{t('operate.decisionInstance.variablesPanel.result')}</TabsTrigger>
					</TabsList>
				</div>
				<TabsContent value={INPUTS_AND_OUTPUTS_TAB} className="mt-0 min-h-0 flex-1 overflow-hidden">
					{inputsAndOutputs}
				</TabsContent>
				<TabsContent
					value={RESULT_TAB}
					forceMount
					className={cn(
						'mt-0 min-h-0 flex-1 overflow-hidden',
						!hasViewedResult && 'hidden',
						'data-[state=inactive]:hidden',
					)}
				>
					{hasViewedResult ? result : null}
				</TabsContent>
			</Tabs>
		</div>
	);
};

const VariablesPanel: React.FC<Props> = ({decisionEvaluationInstanceKey}) => {
	const {t} = useTranslation();
	const {query} = useDecisionInstance(decisionEvaluationInstanceKey);
	const result = <Result query={query} />;

	if (query.data?.decisionDefinitionType === 'LITERAL_EXPRESSION') {
		return (
			<div data-testid="decision-instance-variables-panel" className="flex h-full w-full flex-col overflow-hidden">
				<PanelHeader title={t('operate.decisionInstance.variablesPanel.result')} size="sm" className="bg-transparent" />
				<section className="min-h-0 flex-1">{result}</section>
			</div>
		);
	}

	return <VariablesPanelTabs inputsAndOutputs={<InputsAndOutputs query={query} />} result={result} />;
};

const VariablesPanelPending: React.FC = () => {
	const {t} = useTranslation();

	return (
		<VariablesPanelTabs
			inputsAndOutputs={
				<div className="h-full">
					<InputsAndOutputsLayout>
						<section
							aria-label={t('operate.decisionInstance.variablesPanel.inputVariables')}
							className="grid h-full grid-rows-[auto_1fr] overflow-hidden max-sm:h-auto max-sm:shrink-0"
						>
							<Heading as="h2" variant="heading-sm" className="m-0 mt-4 ml-4 text-muted-foreground">
								{t('operate.decisionInstance.variablesPanel.inputs')}
							</Heading>
							<InputOutputSkeleton columnWidths={inputColumnWidths} data-testid="inputs-skeleton" />
						</section>
						<section
							aria-label={t('operate.decisionInstance.variablesPanel.outputVariables')}
							className="grid h-full grid-rows-[auto_1fr] overflow-hidden max-sm:h-auto max-sm:shrink-0"
						>
							<Heading as="h2" variant="heading-sm" className="m-0 mt-4 ml-4 text-muted-foreground">
								{t('operate.decisionInstance.variablesPanel.outputs')}
							</Heading>
							<InputOutputSkeleton columnWidths={outputColumnWidths} data-testid="outputs-skeleton" />
						</section>
					</InputsAndOutputsLayout>
				</div>
			}
			result={
				<div className="relative flex h-full items-center justify-center overflow-hidden">
					<div data-testid="result-loading-spinner" role="status" aria-live="polite">
						<LoaderCircle className="size-6 animate-spin" aria-hidden />
						<span className="sr-only">{t('appStylesLoading')}</span>
					</div>
				</div>
			}
		/>
	);
};

export {VariablesPanel, VariablesPanelPending};
