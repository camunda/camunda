/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Alert, EmptyState, Link, Skeleton} from '@camunda/design-system';
import {ArrowRightLeft} from '@camunda/design-system/icons';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {getMappings, type MappingType} from '../getMappings';
import {isMappingsHelperBannerHidden, setMappingsHelperBannerHidden} from '../inputOutputMappingsPreference';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {DataLoadErrorAlert} from '#/operate/shared/DataLoadErrorAlert/shadcn.components/DataLoadErrorAlert';

const SKELETON_ROWS = [0, 1, 2];
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
	const {data, isError, isPending, refetch} = useDiagramXml(processInstance.processDefinitionKey);
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
			{isPending ? (
				<div className="flex flex-col gap-2 px-4" aria-busy="true">
					<span className="sr-only">{t('operate.processInstance.mappings.loading')}</span>
					{SKELETON_ROWS.map((row) => (
						<Skeleton key={row} aria-hidden="true" className="h-8 w-full" />
					))}
				</div>
			) : isError ? (
				<DataLoadErrorAlert className="py-0" onRetry={() => void refetch()} />
			) : mappings.length === 0 ? (
				<div className="m-auto">
					<EmptyState
						size="sm"
						headingLevel={3}
						icon={<ArrowRightLeft aria-hidden />}
						heading={t(
							isInput
								? 'operate.processInstance.ds.mappings.noInputHeading'
								: 'operate.processInstance.ds.mappings.noOutputHeading',
						)}
						description={t(
							isInput
								? 'operate.processInstance.ds.mappings.noInputDescription'
								: 'operate.processInstance.ds.mappings.noOutputDescription',
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
					minWidth="32rem"
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
	const {selectedElementId, resolvedElementInstance, isFetchingElementError, refetchElement} =
		useProcessInstanceElementSelection();

	return {
		elementId: selectedElementId ?? resolvedElementInstance?.elementId,
		hasError: isFetchingElementError,
		refetchElement,
	};
}

const MappingsTab: React.FC<{type: MappingType}> = ({type}) => {
	const {elementId, hasError, refetchElement} = useMappingsElementId();

	if (hasError) {
		return <DataLoadErrorAlert onRetry={() => void refetchElement()} />;
	}

	return elementId === undefined ? null : <InputOutputMappings type={type} elementId={elementId} />;
};

const InputMappingsTab: React.FC = () => <MappingsTab type="Input" />;

const OutputMappingsTab: React.FC = () => <MappingsTab type="Output" />;

export {InputMappingsTab, OutputMappingsTab};
