/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {StructuredList} from '#/operate/shared/StructuredList/StructuredList';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {getMappings, type MappingType} from './getMappings';
import {isMappingsHelperBannerHidden, setMappingsHelperBannerHidden} from './inputOutputMappingsPreference';
import {Callout, Content, EmptyMessage} from './inputOutputMappings.styled';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstancePage} from './useProcessInstancePage';

const DOCUMENTATION_URL = 'https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings';

const InfoBanner: React.FC<{type: MappingType; onClose: () => void}> = ({type, onClose}) => {
	const {t} = useTranslation();
	const titleId = useId();
	const isInput = type === 'Input';

	return (
		<Callout
			kind="info"
			lowContrast
			title={t(
				isInput ? 'operate.processInstance.mappings.inputTitle' : 'operate.processInstance.mappings.outputTitle',
			)}
			titleId={titleId}
			subtitle={
				<>
					{t(
						isInput
							? 'operate.processInstance.mappings.inputSubtitle'
							: 'operate.processInstance.mappings.outputSubtitle',
					)}
					<br />
					<br />
					<a aria-describedby={titleId} href={DOCUMENTATION_URL} target="_blank" rel="noopener noreferrer">
						{t('operate.processInstance.mappings.learnMore')}
					</a>
				</>
			}
			actionButtonLabel={t('operate.processInstance.mappings.close')}
			onActionButtonClick={() => {
				onClose();
				setMappingsHelperBannerHidden(type);
			}}
		/>
	);
};

const InputOutputMappings: React.FC<{type: MappingType; elementId: string}> = ({type, elementId}) => {
	const {t} = useTranslation();
	const {processInstance} = useProcessInstancePage();
	const [isInfoBannerVisible, setIsInfoBannerVisible] = useState(() => !isMappingsHelperBannerHidden(type));
	const {data} = useDiagramXml(processInstance.processDefinitionKey);
	const businessObject = data?.businessObjects[elementId];
	const mappings = businessObject === undefined ? [] : getMappings(businessObject, type);
	const isInput = type === 'Input';

	return (
		<Content $isInfoBannerVisible={isInfoBannerVisible}>
			{isInfoBannerVisible && <InfoBanner type={type} onClose={() => setIsInfoBannerVisible(false)} />}
			{mappings.length === 0 ? (
				<EmptyMessage
					message={t(
						isInput ? 'operate.processInstance.mappings.noInput' : 'operate.processInstance.mappings.noOutput',
					)}
				/>
			) : (
				<StructuredList
					label={t(
						isInput
							? 'operate.processInstance.history.inputMappings'
							: 'operate.processInstance.history.outputMappings',
					)}
					headerSize="sm"
					headerColumns={[
						{
							cellContent: t(
								isInput
									? 'operate.processInstance.mappings.localVariableName'
									: 'operate.processInstance.mappings.processVariableName',
							),
						},
						{cellContent: t('operate.processInstance.mappings.assignmentValue')},
					]}
					rows={mappings.map(({source, target}, index) => ({
						key: `${index}:${target}`,
						columns: [{cellContent: target}, {cellContent: source}],
					}))}
				/>
			)}
		</Content>
	);
};

const InputMappingsTab: React.FC = () => {
	const {selectedElementId} = useProcessInstanceElementSelection();

	return selectedElementId === null ? null : <InputOutputMappings type="Input" elementId={selectedElementId} />;
};

const OutputMappingsTab: React.FC = () => {
	const {selectedElementId} = useProcessInstanceElementSelection();

	return selectedElementId === null ? null : <InputOutputMappings type="Output" elementId={selectedElementId} />;
};

export {InputMappingsTab, OutputMappingsTab};
