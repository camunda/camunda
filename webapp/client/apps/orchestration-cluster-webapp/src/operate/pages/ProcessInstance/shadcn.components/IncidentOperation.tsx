/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useIsMutating} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Spinner} from '@camunda/design-system';
import {OperationItems} from '#/operate/shared/OperationItems/shadcn.components/OperationItems';
import {OperationItem} from '#/operate/shared/OperationItem/shadcn.components/OperationItem';
import {useResolveIncident} from '../useResolveIncident';

type Props = {
	incidentKey: string;
	jobKey: string | null;
	disabled: boolean;
};

const IncidentOperation: React.FC<Props> = ({incidentKey, jobKey, disabled}) => {
	const {t} = useTranslation();
	const {mutate, mutationKey} = useResolveIncident({incidentKey, jobKey});
	const isRetryPending = useIsMutating({mutationKey, exact: true}) > 0;

	return (
		<div className="flex items-center justify-end gap-2" data-interactive>
			{isRetryPending && (
				<div role="status" aria-live="polite">
					<Spinner size="default" aria-hidden="true" />
					<span className="sr-only">{t('operate.processInstance.operations.retrying')}</span>
				</div>
			)}
			<OperationItems>
				<OperationItem
					type="RESOLVE_INCIDENT"
					onClick={(event) => {
						event.stopPropagation();
						mutate();
					}}
					title={t('operate.processInstance.incidents.retryIncident')}
					disabled={isRetryPending || disabled}
					size="sm"
				/>
			</OperationItems>
		</div>
	);
};

export {IncidentOperation};
