/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Dialog, DialogBody, DialogContent, DialogHeader, DialogTitle, InlineCode, Link} from '@camunda/design-system';
import {
	ArrowRight,
	CalendarClock,
	CircleCheck,
	KeyRound,
	Layers,
	Network,
	Radio,
	UserRound,
	Wrench,
} from '@camunda/design-system/icons';
import {createLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {AuditLog, AuditLogEntityType} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {cn} from '#/shared/cn';
import {AiAgentIcon} from '../AiAgentIcon';
import {McpIcon} from '../McpIcon';
import {
	formatBatchTitle,
	formatModalHeading,
	hasActorIcon,
	isValidProcessInstanceKey,
	mapToCellDetailsData,
	mapToCellEntityKeyData,
} from '../operationsLogUtils';
import {ActorIcon} from './ActorIcon';
import {OperationsLogResultIcon} from './OperationsLogResultIcon';

type Props = {
	isOpen: boolean;
	onClose: () => void;
	auditLog: AuditLog;
};

type RowProps = {
	icon?: React.ReactNode;
	label: React.ReactNode;
	children: React.ReactNode;
	noWrapLabel?: boolean;
	borderBottom?: boolean;
};

const PARENT_ENTITY_TYPES: AuditLogEntityType[] = ['USER_TASK', 'INCIDENT', 'VARIABLE'];
const UserTaskLink = createLink<React.FC<React.ComponentProps<'a'>>>(Link);
const ProcessInstanceLink = createLink<React.FC<React.ComponentProps<'a'>>>(Link);

function Row({icon, label, children, noWrapLabel = false, borderBottom = true}: RowProps) {
	return (
		<div
			className={cn('grid min-h-11 grid-cols-[minmax(0,50%)_minmax(0,1fr)] items-center gap-4 px-0', {
				'border-b border-border': borderBottom,
			})}
		>
			<dt className={cn('flex items-center gap-2 py-2 pr-4', {'whitespace-nowrap': noWrapLabel})}>
				{icon}
				{label}
			</dt>
			<dd className="min-w-0 py-2">{children}</dd>
		</div>
	);
}

function SectionHeading({children}: {children: React.ReactNode}) {
	return <h3 className="py-[15px] text-sm font-semibold">{children}</h3>;
}

function renderEntityKey(
	auditLog: AuditLog,
	entityKeyData: ReturnType<typeof mapToCellEntityKeyData>,
): React.ReactNode {
	if (auditLog.entityType === 'USER_TASK') {
		return (
			<UserTaskLink
				to="/tasklist/$userTaskKey"
				params={{userTaskKey: entityKeyData.label ?? ''}}
				title={entityKeyData.linkLabel}
				aria-label={entityKeyData.linkLabel}
			>
				{entityKeyData.label}
			</UserTaskLink>
		);
	}

	if (auditLog.entityType === 'PROCESS_INSTANCE') {
		return (
			<ProcessInstanceLink
				to="/operate/processes/$processInstanceId"
				params={{processInstanceId: auditLog.entityKey}}
				title={entityKeyData.linkLabel}
				aria-label={entityKeyData.linkLabel}
			>
				{entityKeyData.label}
			</ProcessInstanceLink>
		);
	}

	if (entityKeyData.link) {
		return (
			<Link href={entityKeyData.link} title={entityKeyData.linkLabel} aria-label={entityKeyData.linkLabel}>
				{entityKeyData.label}
			</Link>
		);
	}

	return entityKeyData.label;
}

const OperationsLogDetailsModal: React.FC<Props> = ({isOpen, onClose, auditLog}) => {
	const {t} = useTranslation();
	const entityKeyData = mapToCellEntityKeyData(
		t,
		auditLog,
		auditLog.processDefinitionId,
		auditLog.decisionDefinitionId,
	);
	const detailsData = mapToCellDetailsData(t, auditLog);

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent size="md" aria-describedby={undefined}>
				<DialogHeader>
					<DialogTitle>{formatModalHeading(auditLog)}</DialogTitle>
				</DialogHeader>
				<DialogBody className="space-y-4">
					{auditLog.entityType !== 'BATCH' && auditLog.batchOperationKey ? (
						<p className="flex items-center gap-2">
							<Layers aria-hidden="true" className="size-4 shrink-0" />
							<span>{t('operate.operationsLog.modal.partOfBatch')}</span>
							<Link href={`/operate/batch-operations/${auditLog.batchOperationKey}`}>
								{t('operate.operationsLog.modal.viewBatchOperationDetailsLink')}
							</Link>
						</p>
					) : null}
					<dl className="border-y border-border">
						<Row
							icon={<CircleCheck aria-hidden="true" className="size-4 shrink-0" />}
							label={t('operate.operationsLog.modal.status')}
							noWrapLabel
						>
							<div className="flex items-center gap-2">
								<OperationsLogResultIcon state={auditLog.result} data-testid={`${auditLog.auditLogKey}-icon`} />
								<span>{spaceAndCapitalize(auditLog.result)}</span>
							</div>
						</Row>
						<Row
							icon={<UserRound aria-hidden="true" className="size-4 shrink-0" />}
							label={t('operate.operationsLog.modal.actor')}
						>
							<div className="space-y-3">
								{!hasActorIcon(auditLog) ? (
									<span>{auditLog.actorId}</span>
								) : (
									<div className="flex items-center gap-2">
										<ActorIcon auditLog={auditLog} aria-hidden="true" className="size-4 shrink-0" />
										<InlineCode className="break-all">{auditLog.actorId}</InlineCode>
									</div>
								)}
								{auditLog.agentElementId ? (
									<div className="flex items-center gap-2">
										<AiAgentIcon aria-hidden="true" className="size-4 shrink-0" />
										<InlineCode className="break-all">{auditLog.agentElementId}</InlineCode>
									</div>
								) : null}
								{auditLog.inboundChannelType ? (
									<div className="flex items-center gap-2">
										{auditLog.inboundChannelType === 'MCP' ? (
											<McpIcon aria-hidden="true" className="size-4 shrink-0" data-testid="mcp-icon" />
										) : (
											<Radio aria-hidden="true" className="size-4 shrink-0" />
										)}
										<span>
											{t('operate.operationsLog.modal.inboundChannel')}{' '}
											<InlineCode className="break-all">{auditLog.inboundChannelType}</InlineCode>
										</span>
									</div>
								) : null}
								{auditLog.inboundChannelToolName ? (
									<div className="flex items-center gap-2">
										<Wrench aria-hidden="true" className="size-4 shrink-0" />
										<span>
											{t('operate.operationsLog.modal.inboundChannelToolName')}{' '}
											<InlineCode className="break-all">{auditLog.inboundChannelToolName}</InlineCode>
										</span>
									</div>
								) : null}
							</div>
						</Row>
						<Row
							icon={<KeyRound aria-hidden="true" className="size-4 shrink-0" />}
							label={t('operate.operationsLog.modal.entityKey')}
							noWrapLabel
						>
							<span>
								{renderEntityKey(auditLog, entityKeyData)} {auditLog.entityDescription?.trim() || entityKeyData.name}
							</span>
						</Row>
						{PARENT_ENTITY_TYPES.includes(auditLog.entityType) &&
						isValidProcessInstanceKey(auditLog.processInstanceKey) ? (
							<Row
								icon={<Network aria-hidden="true" className="size-4 shrink-0" />}
								label={t('operate.operationsLog.modal.parentEntity')}
								noWrapLabel
							>
								<span>
									<ProcessInstanceLink
										to="/operate/processes/$processInstanceId"
										params={{processInstanceId: auditLog.processInstanceKey}}
										aria-label={t('operate.operationsLog.entityLinks.viewProcessInstance', {
											key: auditLog.processInstanceKey,
										})}
									>
										{auditLog.processInstanceKey}
									</ProcessInstanceLink>{' '}
									<em>{auditLog.processDefinitionId}</em>
								</span>
							</Row>
						) : null}
						<Row
							icon={<CalendarClock aria-hidden="true" className="size-4 shrink-0" />}
							label={t('operate.operationsLog.modal.date')}
							noWrapLabel
							borderBottom={false}
						>
							{formatTimestamp(auditLog.timestamp)}
						</Row>
					</dl>
					{auditLog.entityType === 'BATCH' ? (
						<section>
							<SectionHeading>{t('operate.operationsLog.modal.appliedTo')}</SectionHeading>
							<dl className="border-y border-border">
								<Row
									icon={<Layers aria-hidden="true" className="size-4 shrink-0" />}
									label={
										<span className="flex items-center gap-1.5">
											<span>
												{t('operate.operationsLog.modal.multiple')}{' '}
												{formatBatchTitle(t, auditLog.batchOperationType ?? undefined)}
											</span>
											<InlineCode className="break-all">{auditLog.batchOperationKey}</InlineCode>
										</span>
									}
									noWrapLabel
									borderBottom={false}
								>
									<Link
										href={`/operate/batch-operations/${auditLog.batchOperationKey}`}
										aria-label={t('operate.operationsLog.entityLinks.viewBatchOperation', {
											key: auditLog.batchOperationKey,
										})}
										className="inline-flex items-center gap-1"
									>
										{t('operate.operationsLog.modal.viewBatchOperationDetails')}
										<ArrowRight aria-hidden="true" className="size-4 shrink-0" />
									</Link>
								</Row>
							</dl>
						</section>
					) : null}
					{detailsData.property ? (
						<section>
							<SectionHeading>{t('operate.operationsLog.modal.detailsTitle')}</SectionHeading>
							<dl className="border-y border-border">
								<Row label={detailsData.property} borderBottom={false}>
									{detailsData.value}
								</Row>
							</dl>
						</section>
					) : null}
				</DialogBody>
			</DialogContent>
		</Dialog>
	);
};

export {OperationsLogDetailsModal};
