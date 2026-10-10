/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationItems} from '#/operate/shared/OperationItems/shadcn.components/OperationItems';
import {LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {OperationRenderer} from './OperationRenderer';
import type {OperationConfig} from '#/operate/shared/Operations/types';

type Props = {
	operations: OperationConfig[];
	processInstanceKey: string;
	isLoading?: boolean;
	loadingMessage?: string;
};

const Operations: React.FC<Props> = ({operations, processInstanceKey, isLoading = false, loadingMessage}) => {
	const {t} = useTranslation();

	return (
		<div className="flex flex-row items-center gap-2">
			{isLoading ? (
				<div
					className="flex flex-row items-center gap-2"
					data-testid="operation-spinner"
					role="status"
					aria-live="polite"
				>
					<LoaderCircle aria-hidden="true" className="size-4 animate-spin" />
					<span>{loadingMessage || t('operate.shared.operations.loadingMessage', {processInstanceKey})}</span>
				</div>
			) : null}
			<OperationItems>
				{operations.map((operation) => (
					<OperationRenderer key={operation.type} operation={operation} processInstanceKey={processInstanceKey} />
				))}
			</OperationItems>
		</div>
	);
};

export {Operations};
