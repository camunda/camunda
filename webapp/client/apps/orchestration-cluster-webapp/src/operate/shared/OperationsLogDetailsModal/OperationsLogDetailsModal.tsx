/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CodeSnippet, Link, Modal, StructuredListBody, StructuredListWrapper} from '@carbon/react';
import {
	ArrowRight,
	BatchJob,
	CheckmarkOutline,
	Channels,
	EventSchedule,
	ParentNode,
	Password,
	Tools,
	UserAvatar,
} from '@carbon/react/icons';
import {Link as RouterLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {formatTimestamp} from '#/operate/shared/utils/formatTimestamp';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {ActorIcon} from './ActorIcon';
import {AiAgentIcon} from './AiAgentIcon';
import {McpIcon} from './McpIcon';
import {OperationsLogResultIcon} from './OperationsLogResultIcon';
import {
	formatBatchTitle,
	formatModalHeading,
	hasActorIcon,
	isValidProcessInstanceKey,
	mapToCellDetailsData,
	mapToCellEntityKeyData,
} from './operationsLogUtils';
import {
	FirstColumn,
	IconText,
	IconTextWithTopMargin,
	ParagraphWithIcon,
	SecondColumn,
	TitleListCell,
	VerticallyAlignedRow,
} from './styled';

type Props = {
	isOpen: boolean;
	onClose: () => void;
	auditLog: AuditLog;
};

type DetailsModalState = {
	isOpen: boolean;
	auditLog?: AuditLog;
};

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
		<Modal
			size="md"
			open={isOpen}
			onRequestClose={onClose}
			modalHeading={formatModalHeading(auditLog)}
			primaryButtonDisabled
			passiveModal
		>
			{auditLog.entityType !== 'BATCH' && auditLog.batchOperationKey ? (
				<ParagraphWithIcon>
					<BatchJob />
					{t('operate.operationsLog.modal.partOfBatch')}
					<Link href={`/operate/batch-operations/${auditLog.batchOperationKey}`}>
						{t('operate.operationsLog.modal.viewBatchOperationDetailsLink')}
					</Link>
				</ParagraphWithIcon>
			) : undefined}
			<StructuredListWrapper isCondensed isFlush>
				<StructuredListBody>
					<VerticallyAlignedRow>
						<FirstColumn noWrap>
							<IconText>
								<CheckmarkOutline />
								{t('operate.operationsLog.modal.status')}
							</IconText>
						</FirstColumn>
						<SecondColumn>
							<IconText>
								<OperationsLogResultIcon state={auditLog.result} data-testid={`${auditLog.auditLogKey}-icon`} />
								{spaceAndCapitalize(auditLog.result)}
							</IconText>
						</SecondColumn>
					</VerticallyAlignedRow>
					<VerticallyAlignedRow>
						<FirstColumn>
							<IconText>
								<UserAvatar />
								{t('operate.operationsLog.modal.actor')}
							</IconText>
						</FirstColumn>
						<SecondColumn>
							{!hasActorIcon(auditLog) ? (
								auditLog.actorId
							) : (
								<IconText>
									<ActorIcon auditLog={auditLog} />
									<CodeSnippet type="inline" wrapText>
										{auditLog.actorId}
									</CodeSnippet>
								</IconText>
							)}
							{auditLog.agentElementId && (
								<IconTextWithTopMargin>
									<AiAgentIcon />
									<CodeSnippet type="inline" wrapText>
										{auditLog.agentElementId}
									</CodeSnippet>
								</IconTextWithTopMargin>
							)}
							{auditLog.inboundChannelType && (
								<IconTextWithTopMargin>
									{auditLog.inboundChannelType === 'MCP' ? <McpIcon data-testid="mcp-icon" /> : <Channels />}
									<span>
										{t('operate.operationsLog.modal.inboundChannel')}{' '}
										<CodeSnippet type="inline" wrapText>
											{auditLog.inboundChannelType}
										</CodeSnippet>
									</span>
								</IconTextWithTopMargin>
							)}
							{auditLog.inboundChannelToolName && (
								<IconTextWithTopMargin>
									<Tools />
									<span>
										{t('operate.operationsLog.modal.inboundChannelToolName')}{' '}
										<CodeSnippet type="inline" wrapText>
											{auditLog.inboundChannelToolName}
										</CodeSnippet>
									</span>
								</IconTextWithTopMargin>
							)}
						</SecondColumn>
					</VerticallyAlignedRow>
					<VerticallyAlignedRow>
						<FirstColumn noWrap>
							<IconText>
								<Password />
								{t('operate.operationsLog.modal.entityKey')}
							</IconText>
						</FirstColumn>
						<SecondColumn>
							{auditLog.entityType === 'USER_TASK' ? (
								<RouterLink
									to="/tasklist/$userTaskKey"
									params={{userTaskKey: entityKeyData.label ?? ''}}
									title={entityKeyData.linkLabel}
									aria-label={entityKeyData.linkLabel}
								>
									{entityKeyData.label}
								</RouterLink>
							) : entityKeyData.link ? (
								<Link href={entityKeyData.link} title={entityKeyData.linkLabel} aria-label={entityKeyData.linkLabel}>
									{entityKeyData.label}
								</Link>
							) : (
								entityKeyData.label
							)}
							&nbsp;
							{auditLog.entityDescription?.trim() || entityKeyData.name}
						</SecondColumn>
					</VerticallyAlignedRow>
					{(['USER_TASK', 'INCIDENT', 'VARIABLE'] as const).includes(
						auditLog.entityType as 'USER_TASK' | 'INCIDENT' | 'VARIABLE',
					) && isValidProcessInstanceKey(auditLog.processInstanceKey) ? (
						<VerticallyAlignedRow>
							<FirstColumn noWrap>
								<IconText>
									<ParentNode />
									{t('operate.operationsLog.modal.parentEntity')}
								</IconText>
							</FirstColumn>
							<SecondColumn>
								<Link
									href={`/operate/processes/${auditLog.processInstanceKey}`}
									aria-label={t('operate.operationsLog.entityLinks.viewProcessInstance', {
										key: auditLog.processInstanceKey,
									})}
								>
									{auditLog.processInstanceKey}
								</Link>
								&nbsp;
								<em>{auditLog.processDefinitionId}</em>
							</SecondColumn>
						</VerticallyAlignedRow>
					) : undefined}
					<VerticallyAlignedRow>
						<FirstColumn noWrap>
							<IconText>
								<EventSchedule />
								{t('operate.operationsLog.modal.date')}
							</IconText>
						</FirstColumn>
						<SecondColumn>{formatTimestamp(auditLog.timestamp)}</SecondColumn>
					</VerticallyAlignedRow>
					{auditLog.entityType === 'BATCH' && (
						<>
							<VerticallyAlignedRow>
								<TitleListCell>{t('operate.operationsLog.modal.appliedTo')}</TitleListCell>
							</VerticallyAlignedRow>
							<VerticallyAlignedRow>
								<FirstColumn noWrap>
									<IconText>
										<BatchJob />
										{t('operate.operationsLog.modal.multiple')}{' '}
										{formatBatchTitle(t, auditLog.batchOperationType ?? undefined)}
										<CodeSnippet type="inline">{auditLog.batchOperationKey}</CodeSnippet>
									</IconText>
								</FirstColumn>
								<SecondColumn>
									<IconText>
										<Link
											href={`/operate/batch-operations/${auditLog.batchOperationKey}`}
											aria-label={t('operate.operationsLog.entityLinks.viewBatchOperation', {
												key: auditLog.batchOperationKey,
											})}
										>
											{t('operate.operationsLog.modal.viewBatchOperationDetails')}
											<ArrowRight />
										</Link>
									</IconText>
								</SecondColumn>
							</VerticallyAlignedRow>
						</>
					)}
					{detailsData.property ? (
						<>
							<VerticallyAlignedRow>
								<TitleListCell>{t('operate.operationsLog.modal.detailsTitle')}</TitleListCell>
							</VerticallyAlignedRow>
							<VerticallyAlignedRow>
								<FirstColumn noWrap>{detailsData.property}</FirstColumn>
								<SecondColumn>{detailsData.value}</SecondColumn>
							</VerticallyAlignedRow>
						</>
					) : undefined}
				</StructuredListBody>
			</StructuredListWrapper>
		</Modal>
	);
};

export type {DetailsModalState};
export {OperationsLogDetailsModal};
