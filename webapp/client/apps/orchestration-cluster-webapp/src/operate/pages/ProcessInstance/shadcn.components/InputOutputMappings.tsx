/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Alert, Link} from '@camunda/design-system';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/shadcn.components/ErrorMessage';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {getMappings, type MappingType} from '../getMappings';
import {isMappingsHelperBannerHidden, setMappingsHelperBannerHidden} from '../inputOutputMappingsPreference';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';

const DOCUMENTATION_URL = 'https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings';

const InfoBanner: React.FC<{type: MappingType; onClose: () => void}> = ({type, onClose}) => {
	const {t} = useTranslation();
	const descriptionId = useId();
	const isInput = type === 'Input';

	return (
		<Alert
			variant="info"
			title={t(
				isInput ? 'operate.processInstance.mappings.inputTitle' : 'operate.processInstance.mappings.outputTitle',
			)}
			description={
				<div id={descriptionId} className="flex flex-col gap-3">
					<span>
						{t(
							isInput
								? 'operate.processInstance.mappings.inputSubtitle'
								: 'operate.processInstance.mappings.outputSubtitle',
						)}
					</span>
					<Link href={DOCUMENTATION_URL} target="_blank" rel="noopener noreferrer" aria-describedby={descriptionId}>
						{t('operate.processInstance.mappings.learnMore')}
					</Link>
				</div>
			}
			dismissible
			dismissLabel={t('operate.processInstance.mappings.close')}
			onDismiss={() => {
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
	const {data, isError, isPending} = useDiagramXml(processInstance.processDefinitionKey);
	const businessObject = data?.businessObjects[elementId];
	const mappings = businessObject === undefined ? [] : getMappings(businessObject, type);
	const isInput = type === 'Input';

	return (
		<div className="flex h-full min-h-0 flex-col gap-4 py-4">
			{isInfoBannerVisible && (
				<div className="px-4">
					<InfoBanner type={type} onClose={() => setIsInfoBannerVisible(false)} />
				</div>
			)}
			{isPending ? null : isError ? (
				<div className="m-auto">
					<ErrorMessage />
				</div>
			) : mappings.length === 0 ? (
				<div className="m-auto">
					<EmptyMessage
						message={t(
							isInput ? 'operate.processInstance.mappings.noInput' : 'operate.processInstance.mappings.noOutput',
						)}
					/>
				</div>
			) : (
				<StructuredList
					label={t(
						isInput
							? 'operate.processInstance.history.inputMappings'
							: 'operate.processInstance.history.outputMappings',
					)}
					className="[&_table]:min-w-[32rem] [&_table]:table-fixed"
					isFlush={false}
					cellClassName="align-middle"
					containerClassName="rounded-none border-0 shadow-none"
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
						columns: [{cellContent: <TruncatedText text={target} />}, {cellContent: <TruncatedText text={source} />}],
					}))}
				/>
			)}
		</div>
	);
};

function useMappingsElementId() {
	const {selectedElementId, resolvedElementInstance, isFetchingElementError} = useProcessInstanceElementSelection();

	return {elementId: selectedElementId ?? resolvedElementInstance?.elementId, hasError: isFetchingElementError};
}

const MappingsTab: React.FC<{type: MappingType}> = ({type}) => {
	const {elementId, hasError} = useMappingsElementId();

	if (hasError) {
		return (
			<div className="m-auto">
				<ErrorMessage />
			</div>
		);
	}

	return elementId === undefined ? null : <InputOutputMappings type={type} elementId={elementId} />;
};

const InputMappingsTab: React.FC = () => <MappingsTab type="Input" />;

const OutputMappingsTab: React.FC = () => <MappingsTab type="Output" />;

export {InputMappingsTab, OutputMappingsTab};
