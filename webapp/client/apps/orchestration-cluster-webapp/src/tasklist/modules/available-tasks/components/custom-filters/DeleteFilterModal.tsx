/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import {Trans, useTranslation} from 'react-i18next';
import {getStateLocally} from '#/shared/browser-storage/local-storage';

type Props = {
	isOpen: boolean;
	filterId: string;
	onClose: () => void;
	onDelete: (filterId: string) => void;
	'data-testid'?: string;
};

const DeleteFilterModal: React.FC<Props> = ({isOpen, onClose, onDelete, filterId, ...props}) => {
	const {t} = useTranslation();
	const filterName = getStateLocally('tasklist.customFilters')?.[filterId]?.name;

	return (
		<Modal
			{...props}
			danger
			open={isOpen}
			size="sm"
			modalHeading={isOpen ? t('tasklist.customFiltersModalDeleteModalHeading') : undefined}
			primaryButtonText={t('tasklist.customFiltersModalConfirmDeletionButton')}
			secondaryButtonText={t('tasklist.tasksFiltersModalCancelButtonLabel')}
			onRequestClose={onClose}
			onRequestSubmit={() => {
				onDelete(filterId);
			}}
		>
			<p>
				<Trans
					i18nKey="tasklist.customFiltersModalDeleteModalBody"
					values={{name: filterName}}
					components={{strong: <strong />}}
				/>
			</p>
		</Modal>
	);
};

export {DeleteFilterModal};
