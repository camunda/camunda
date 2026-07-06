/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	ComposedModal,
	ModalBody,
	ModalHeader,
	StructuredListBody,
	StructuredListCell,
	StructuredListRow,
	StructuredListWrapper,
} from '@carbon/react';
import {EventSchedule, UserAvatar} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatHistoryDate} from '#/tasklist/modules/task-details-history/formatHistoryDate';
import {getOperationTypeTranslationKey} from '#/tasklist/modules/task-details-history/getOperationTypeTranslationKey';
import styles from './HistoryItemDetailsModal.module.scss';

type Props = {
	onClose: () => void;
	auditLog: AuditLog;
};

const HistoryItemDetailsModal: React.FC<Props> = ({onClose, auditLog}) => {
	const {t} = useTranslation();

	return (
		<ComposedModal
			size="md"
			open
			onClose={onClose}
			aria-label={t(getOperationTypeTranslationKey(auditLog.operationType))}
		>
			<ModalHeader
				title={t(getOperationTypeTranslationKey(auditLog.operationType))}
				buttonOnClick={onClose}
				iconDescription={t('tasklist.taskDetailsHistoryModalClose')}
			/>
			<ModalBody>
				<StructuredListWrapper isCondensed isFlush>
					<StructuredListBody>
						<StructuredListRow className={styles.verticallyAlignedRow}>
							<StructuredListCell className={styles.firstColumn}>
								<div className={styles.iconText}>
									<UserAvatar />
									{t('tasklist.taskDetailsHistoryModalActor')}
								</div>
							</StructuredListCell>
							<StructuredListCell>{auditLog.actorId}</StructuredListCell>
						</StructuredListRow>
						<StructuredListRow className={styles.verticallyAlignedRow}>
							<StructuredListCell noWrap className={styles.firstColumn}>
								<div className={styles.iconText}>
									<EventSchedule />
									{t('tasklist.taskDetailsHistoryModalTime')}
								</div>
							</StructuredListCell>
							<StructuredListCell>{formatHistoryDate(auditLog.timestamp)}</StructuredListCell>
						</StructuredListRow>
						{auditLog.operationType === 'ASSIGN' ? (
							<>
								<StructuredListRow>
									<h5 className={styles.titleListCell}>{t('tasklist.taskDetailsHistoryModalDetails')}:</h5>
								</StructuredListRow>
								<StructuredListRow className={styles.verticallyAlignedRow}>
									<StructuredListCell noWrap className={styles.firstColumn}>
										<div className={styles.iconText}>
											<EventSchedule />
											{t('tasklist.taskDetailsHistoryModalAssignee')}
										</div>
									</StructuredListCell>
									<StructuredListCell>{auditLog.relatedEntityKey}</StructuredListCell>
								</StructuredListRow>
							</>
						) : null}
					</StructuredListBody>
				</StructuredListWrapper>
			</ModalBody>
		</ComposedModal>
	);
};

export {HistoryItemDetailsModal};
