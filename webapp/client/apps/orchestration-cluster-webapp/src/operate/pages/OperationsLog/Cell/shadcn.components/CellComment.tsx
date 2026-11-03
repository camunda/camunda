/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {IconButton} from '@camunda/design-system';
import {Info} from '@camunda/design-system/icons';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import type {DetailsModalState} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/OperationsLogDetailsModal';

type Props = {
	item: AuditLog;
	setDetailsModal: (state: DetailsModalState) => void;
};

const CellComment: React.FC<Props> = ({item, setDetailsModal}) => {
	const {t} = useTranslation();

	return (
		<IconButton
			variant="ghost"
			size="sm"
			label={t('operate.operationsLog.comment.openDetails')}
			tooltipSide="bottom"
			icon={Info}
			onClick={() => setDetailsModal({isOpen: true, auditLog: item})}
		/>
	);
};

export {CellComment};
