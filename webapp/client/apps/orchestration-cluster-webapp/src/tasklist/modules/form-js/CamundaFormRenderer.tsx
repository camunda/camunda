/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';
import {Layer, Stack} from '@carbon/react';
import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.10';
import set from 'lodash/set';
import {useTranslation} from 'react-i18next';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {FormManager} from './FormManager';
import {mergeVariables} from './mergeVariables';
import {ValidationMessage} from './ValidationMessage';
import {getFieldLabels} from './getFieldLabels';
import {usePrefersReducedMotion} from './usePrefersReducedMotion';
import {FormLevelErrorMessage} from './FormLevelErrorMessage';
import {toHumanReadableBytes} from './toHumanReadableBytes';
import {extractFilePath} from './extractFilePath';
import styles from './CamundaFormRenderer.module.scss';
import '@bpmn-io/form-js-viewer/dist/assets/form-js-base.css';
import '@bpmn-io/form-js-carbon-styles/src/carbon-styles.scss';

type FormManagerRef = {
	current: FormManager;
};

type PartialVariable = {
	name: string;
	value: string;
};

type Props = {
	handleSubmit: (variables: PartialVariable[]) => Promise<void>;
	handleFileUpload?: (files: Map<string, File[]>) => Promise<Map<string, DocumentReference[]>>;
	schema: string;
	data?: Record<string, unknown>;
	readOnly?: boolean;
	onMount?: (formManager: FormManager) => void;
	onRender?: () => void;
	onImportError?: () => void;
	onSubmitStart?: () => void;
	onSubmitError?: (error: unknown) => void;
	onSubmitSuccess?: () => void;
	onValidationError?: () => void;
};

function htmlDomId(fieldId: string, formId?: string, indices?: string[]): string {
	const result = ['fjs-form'];
	if (formId) {
		result.push('-', formId);
	}
	result.push('-', fieldId);
	if (indices) {
		result.push(indices.join(''));
	}
	return result.join('');
}

function useScrollToError(managerRef: FormManagerRef) {
	const prefersReducedMotion = usePrefersReducedMotion();

	return useCallback(
		(fieldId: string) => {
			if (prefersReducedMotion) {
				return;
			}

			const manager = managerRef.current;

			const form = manager.get('form');
			const ffr = manager.get('formFieldRegistry');
			const field = ffr.get(fieldId);
			const indices: string[] = [];

			if (field._path.length > 2) {
				let parent = field;
				while (parent._path.length > 2) {
					parent = ffr.get(parent._parent);
					if (parent.type === 'dynamiclist') {
						indices.push('_0');
					}
				}
			}

			let firstInvalidDomId: string | undefined;
			if (field.type === 'radio' || field.type === 'checklist') {
				firstInvalidDomId = htmlDomId(fieldId, form._id, [...indices, '-0']);
			} else if (field.type === 'datetime') {
				firstInvalidDomId = htmlDomId(fieldId, form._id, [...indices, '-date']);
			} else {
				firstInvalidDomId = htmlDomId(fieldId, form._id, indices);
			}

			document.getElementById(firstInvalidDomId)?.scrollIntoView({behavior: 'auto', block: 'center'});
		},
		[managerRef, prefersReducedMotion],
	);
}

function injectFileMetadataIntoData(options: {
	data: Record<string, unknown>;
	fileMetadata: Map<string, DocumentReference[]>;
	pathsToInject: Map<string, string>;
}): Record<string, unknown> {
	const {data, fileMetadata, pathsToInject} = options;
	let result = structuredClone(data);

	pathsToInject.forEach((filepickerPath, fileKey) => {
		const metadata = fileMetadata.get(fileKey);

		if (metadata === undefined) {
			return;
		}

		result = set(result, filepickerPath, metadata);
	});

	return result;
}

const CamundaFormRenderer: React.FC<Props> = ({
	handleSubmit,
	schema,
	data = {},
	readOnly,
	onMount,
	onRender,
	onImportError,
	onSubmitStart,
	onSubmitError,
	onSubmitSuccess,
	onValidationError,
	handleFileUpload = () => Promise.resolve(new Map()),
}) => {
	const formManagerRef = useRef<FormManager>(new FormManager());
	const formContainerRef = useRef<HTMLDivElement | null>(null);
	const [invalidFields, setInvalidFields] = useState<{ids: string[]; labels: string[]} | undefined>();
	const [hasLargeFilePayload, setHasLargeFilePayload] = useState(false);
	const scrollToError = useScrollToError(formManagerRef);
	const hasInvalidFields = invalidFields !== undefined;
	const {t} = useTranslation();

	useEffect(() => {
		onMount?.(formManagerRef.current);
	}, [onMount]);

	useEffect(() => {
		function render() {
			const formManager = formManagerRef.current;
			const container = formContainerRef.current;

			if (container === null) {
				return;
			}

			onRender?.();
			formManager.render({
				container,
				schema,
				data,
				onImportError,
				onSubmit: async ({data: newData, errors, files = new Map<string, File[]>()}) => {
					onSubmitStart?.();
					setInvalidFields(undefined);
					setHasLargeFilePayload(false);
					const fieldIds = Object.keys(errors);
					const hasFieldErrors = fieldIds.length > 0;

					if (hasFieldErrors) {
						onValidationError?.();
						setInvalidFields({ids: fieldIds, labels: getFieldLabels(formManager, fieldIds)});

						if (fieldIds[0] !== undefined) {
							scrollToError(fieldIds[0]);
						}
					}

					const totalFilePayloadSize = Array.from(files.values())
						.flat()
						.map((file) => file.size)
						.reduce((total, itemSize) => total + itemSize, 0);
					const maxRequestSize = getClientConfig().deployment.maxRequestSize;
					const hasLargeFilePayload = totalFilePayloadSize > maxRequestSize;

					if (hasLargeFilePayload) {
						onValidationError?.();
						setHasLargeFilePayload(true);
					}

					if (hasFieldErrors || hasLargeFilePayload) {
						return;
					}

					try {
						const enrichedData =
							files.size === 0
								? newData
								: injectFileMetadataIntoData({
										data: newData,
										fileMetadata: await handleFileUpload(files),
										pathsToInject: extractFilePath(newData),
									});
						const variables = Object.entries(mergeVariables(data, enrichedData)).map(([name, value]) => ({
							name,
							value: JSON.stringify(value),
						}));

						await handleSubmit(variables);
						onSubmitSuccess?.();
					} catch (error) {
						onSubmitError?.(error);
					}
				},
			});
		}

		render();
	}, [
		schema,
		handleSubmit,
		handleFileUpload,
		onRender,
		onImportError,
		onSubmitStart,
		onSubmitSuccess,
		onSubmitError,
		onValidationError,
		data,
		scrollToError,
	]);

	useEffect(() => {
		const formManager = formManagerRef.current;

		return () => {
			formManager.detach();
		};
	}, []);

	useEffect(() => {
		formManagerRef.current.setReadOnly(Boolean(readOnly));
	}, [readOnly]);

	return (
		<Layer className={styles.container}>
			<div ref={formContainerRef} className={styles.formRoot} />

			{hasInvalidFields || hasLargeFilePayload ? (
				<Stack orientation="vertical" className={styles.formLevelErrorContainer} gap={3}>
					<hr className={styles.hr} />
					{hasInvalidFields ? (
						<ValidationMessage fieldIds={invalidFields.ids} fieldLabels={invalidFields.labels} />
					) : null}
					{hasLargeFilePayload ? (
						<FormLevelErrorMessage
							readableMessage={t('tasklist.formJSLargeFilePayloadError', {
								size: toHumanReadableBytes(getClientConfig().deployment.maxRequestSize),
							})}
						/>
					) : null}
				</Stack>
			) : null}
		</Layer>
	);
};

export {CamundaFormRenderer};
export type {PartialVariable};
