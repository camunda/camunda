/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Loading, Tab, TabList, TabPanels, Tabs} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {PanelHeader} from '#/operate/shared/PanelHeader/PanelHeader';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/ResizablePanel';
import {useDecisionInstance} from './decisionInstance.queries';
import {InputsAndOutputs} from './InputsAndOutputs';
import {InputOutputSkeleton} from './InputOutputSkeleton';
import {inputColumnWidths, outputColumnWidths} from './inputOutputColumns';
import {Result} from './Result';
import {
	Content,
	InputOutputContainer,
	InputOutputSection,
	Panel,
	ResultContainer,
	Title,
	VariablesContainer,
} from './variablesPanel.styled';

type Props = {
	decisionEvaluationInstanceKey: string;
};

type TabsProps = {
	inputsAndOutputs: React.ReactNode;
	result: React.ReactNode;
};

const VariablesPanelTabs: React.FC<TabsProps> = ({inputsAndOutputs, result}) => {
	const {t} = useTranslation();
	const [hasViewedResult, setHasViewedResult] = useState(false);

	return (
		<VariablesContainer data-testid="decision-instance-variables-panel">
			<Tabs
				onChange={({selectedIndex}) => {
					if (selectedIndex === 1) {
						setHasViewedResult(true);
					}
				}}
			>
				<TabList aria-label={t('operate.decisionInstance.variablesPanel.tabsLabel')}>
					<Tab>{t('operate.decisionInstance.variablesPanel.inputsAndOutputs')}</Tab>
					<Tab>{t('operate.decisionInstance.variablesPanel.result')}</Tab>
				</TabList>
				<TabPanels>
					<Panel>{inputsAndOutputs}</Panel>
					<Panel>{hasViewedResult ? result : null}</Panel>
				</TabPanels>
			</Tabs>
		</VariablesContainer>
	);
};

const VariablesPanel: React.FC<Props> = ({decisionEvaluationInstanceKey}) => {
	const {t} = useTranslation();
	const {query} = useDecisionInstance(decisionEvaluationInstanceKey);
	const result = <Result query={query} />;

	if (query.data?.decisionDefinitionType === 'LITERAL_EXPRESSION') {
		return (
			<VariablesContainer data-testid="decision-instance-variables-panel">
				<PanelHeader title={t('operate.decisionInstance.variablesPanel.result')} size="sm" />
				<Content>{result}</Content>
			</VariablesContainer>
		);
	}

	return <VariablesPanelTabs inputsAndOutputs={<InputsAndOutputs query={query} />} result={result} />;
};

const VariablesPanelPending: React.FC = () => {
	const {t} = useTranslation();

	return (
		<VariablesPanelTabs
			inputsAndOutputs={
				<InputOutputContainer>
					<ResizablePanel panelId="decision-instance-horizontal-panel" direction={SplitDirection.Horizontal}>
						<InputOutputSection aria-label={t('operate.decisionInstance.variablesPanel.inputVariables')}>
							<Title>{t('operate.decisionInstance.variablesPanel.inputs')}</Title>
							<InputOutputSkeleton columnWidths={inputColumnWidths} data-testid="inputs-skeleton" />
						</InputOutputSection>
						<InputOutputSection aria-label={t('operate.decisionInstance.variablesPanel.outputVariables')}>
							<Title>{t('operate.decisionInstance.variablesPanel.outputs')}</Title>
							<InputOutputSkeleton columnWidths={outputColumnWidths} data-testid="outputs-skeleton" />
						</InputOutputSection>
					</ResizablePanel>
				</InputOutputContainer>
			}
			result={
				<ResultContainer>
					<Loading data-testid="result-loading-spinner" withOverlay={false} />
				</ResultContainer>
			}
		/>
	);
};

export {VariablesPanel, VariablesPanelPending};
