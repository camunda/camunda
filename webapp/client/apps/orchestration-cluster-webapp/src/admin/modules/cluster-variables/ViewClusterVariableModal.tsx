/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button, DialogBody, DialogFooter, toast} from '@camunda/design-system';
import type {ClusterVariable} from '@camunda/camunda-api-zod-schemas/8.11';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {ClusterVariableDialog} from './ClusterVariableDialog';
import {ClusterVariableValueField} from './ClusterVariableValueField';
import {ClusterVariableValueState} from './ClusterVariableValueState';
import {useClusterVariableValue} from './useClusterVariableValue';

type Props = {
	clusterVariable: ClusterVariable | null;
	onClose: () => void;
};

const ViewClusterVariableModal: React.FC<Props> = ({clusterVariable, onClose}) => {
	if (clusterVariable === null) {
		return null;
	}

	return (
		<ClusterVariableDialog title={clusterVariable.name} onClose={onClose}>
			<ViewContent clusterVariable={clusterVariable} onClose={onClose} />
		</ClusterVariableDialog>
	);
};

const ViewContent: React.FC<{clusterVariable: ClusterVariable; onClose: () => void}> = ({clusterVariable, onClose}) => {
	const {t} = useTranslation();
	const {data, status} = useClusterVariableValue(clusterVariable);

	return (
		<>
			<DialogBody>
				{status === 'success' ? (
					<ClusterVariableValueField
						id="clusterVariableValue"
						label={t('admin.clusterVariables.valueFieldLabel')}
						value={beautifyJSON(data.value)}
						readOnly
						autoFocus={false}
					/>
				) : (
					<ClusterVariableValueState status={status} />
				)}
			</DialogBody>
			<DialogFooter>
				<Button
					type="button"
					variant="secondary"
					disabled={status !== 'success'}
					onClick={async () => {
						if (status !== 'success') {
							return;
						}
						try {
							await navigator.clipboard.writeText(data.value);
							toast.success(t('admin.clusterVariables.copyValueSuccess', {name: clusterVariable.name}));
						} catch {
							toast.error(t('admin.clusterVariables.copyValueError'));
						}
					}}
				>
					{t('admin.clusterVariables.copyValueButton')}
				</Button>
				<Button type="button" onClick={onClose}>
					{t('admin.clusterVariables.closeButton')}
				</Button>
			</DialogFooter>
		</>
	);
};

export {ViewClusterVariableModal};
