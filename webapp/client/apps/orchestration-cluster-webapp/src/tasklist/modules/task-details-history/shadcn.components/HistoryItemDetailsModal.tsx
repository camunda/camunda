/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Button,
	Dialog,
	DialogBody,
	DialogClose,
	DialogContent,
	DialogHeader,
	DialogTitle,
} from '@camunda/design-system';
import {CalendarClock, CircleUser, X} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatHistoryDate} from '#/tasklist/modules/task-details-history/formatHistoryDate';
import {getOperationTypeTranslationKey} from '#/tasklist/modules/task-details-history/getOperationTypeTranslationKey';

type Props = {
	onClose: () => void;
	auditLog: AuditLog;
};

const HistoryItemDetailsModal: React.FC<Props> = ({onClose, auditLog}) => {
	const {t} = useTranslation();

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent size="sm" showCloseButton={false} aria-describedby={undefined}>
				<DialogHeader>
					<DialogTitle>{t(getOperationTypeTranslationKey(auditLog.operationType))}</DialogTitle>
				</DialogHeader>
				<DialogBody>
					<dl className="border-y border-border">
						<div className="grid min-h-11 grid-cols-[40%_60%] items-center border-b border-border">
							<dt className="flex items-center gap-1 py-2 pr-4">
								<CircleUser aria-hidden />
								{t('tasklist.taskDetailsHistoryModalActor')}
							</dt>
							<dd className="py-2">{auditLog.actorId}</dd>
						</div>
						<div className="grid min-h-11 grid-cols-[40%_60%] items-center">
							<dt className="flex items-center gap-1 py-2 pr-4 whitespace-nowrap">
								<CalendarClock aria-hidden />
								{t('tasklist.taskDetailsHistoryModalTime')}
							</dt>
							<dd className="py-2">{formatHistoryDate(auditLog.timestamp)}</dd>
						</div>
					</dl>
					{auditLog.operationType === 'ASSIGN' ? (
						<section>
							<h3 className="py-4 font-semibold">{t('tasklist.taskDetailsHistoryModalDetails')}:</h3>
							<dl className="border-y border-border">
								<div className="grid min-h-11 grid-cols-[40%_60%] items-center">
									<dt className="flex items-center gap-1 py-2 pr-4 whitespace-nowrap">
										<CircleUser aria-hidden />
										{t('tasklist.taskDetailsHistoryModalAssignee')}
									</dt>
									<dd className="py-2">{auditLog.relatedEntityKey}</dd>
								</div>
							</dl>
						</section>
					) : null}
				</DialogBody>
				<DialogClose asChild>
					<Button
						type="button"
						variant="ghost"
						size="icon-sm"
						className="absolute top-2 right-2"
						aria-label={t('tasklist.taskDetailsHistoryModalClose')}
					>
						<X aria-hidden />
					</Button>
				</DialogClose>
			</DialogContent>
		</Dialog>
	);
};

export {HistoryItemDetailsModal};
