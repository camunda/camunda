/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  CodeSnippet,
  Modal,
  StructuredListBody,
  StructuredListWrapper,
} from '@carbon/react';
import {Link} from 'modules/components/Link';
import {formatDate} from 'modules/utils/date';
import {
  ArrowRight,
  BatchJob,
  CheckmarkOutline,
  EventSchedule,
  UserAvatar,
  Password,
  ParentNode,
} from '@carbon/react/icons';
import {
  TitleListCell,
  FirstColumn,
  IconText,
  ParagraphWithIcon,
  VerticallyAlignedRow,
  IconTextWithTopMargin,
  SecondColumn,
} from './styled';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {OperationsLogStateIcon} from 'modules/components/OperationsLogStateIcon';
import {
  formatBatchTitle,
  formatModalHeading,
  getActorIcon,
  isValidProcessInstanceKey,
  mapToCellDetailsData,
  mapToCellEntityKeyData,
} from 'modules/utils/operationsLog';
import {Paths} from 'modules/Routes';
import {useMemo} from 'react';
import AiAgentIcon from 'modules/components/Icon/ai-agent.svg?react';
import {getClientConfig} from 'modules/utils/getClientConfig';

type Props = {
  isOpen: boolean;
  onClose: () => void;
  auditLog: AuditLog;
};

type DetailsModalState = {
  isOpen: boolean;
  auditLog?: AuditLog;
};

const DetailsModal: React.FC<Props> = ({isOpen, onClose, auditLog}) => {
  const ActorIcon = useMemo(() => getActorIcon(auditLog), [auditLog]);
  const clientConfig = getClientConfig();
  const entityKeyData = mapToCellEntityKeyData(
    auditLog,
    auditLog.processDefinitionId,
    auditLog.decisionDefinitionId,
    clientConfig?.tasklistUrl ?? undefined,
  );
  const detailsData = mapToCellDetailsData(auditLog);

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
          This operation is part of a batch.
          <Link
            to={Paths.batchOperation(auditLog.batchOperationKey)}
            aria-label={`View batch operation ${auditLog.batchOperationKey}`}
          >
            View batch operation details.
          </Link>
        </ParagraphWithIcon>
      ) : undefined}
      <StructuredListWrapper isCondensed isFlush>
        <StructuredListBody>
          <VerticallyAlignedRow>
            <FirstColumn noWrap>
              <IconText>
                <CheckmarkOutline />
                Status
              </IconText>
            </FirstColumn>
            <SecondColumn>
              <IconText>
                <OperationsLogStateIcon
                  state={auditLog.result}
                  data-testid={`${auditLog.auditLogKey}-icon`}
                />
                {spaceAndCapitalize(auditLog.result.toString())}
              </IconText>
            </SecondColumn>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn>
              <IconText>
                <UserAvatar />
                Actor
              </IconText>
            </FirstColumn>
            <SecondColumn>
              {!ActorIcon ? (
                auditLog.actorId
              ) : (
                <IconText>
                  <ActorIcon />
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
            </SecondColumn>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn noWrap>
              <IconText>
                <Password />
                Entity key
              </IconText>
            </FirstColumn>
            <SecondColumn>
              {entityKeyData.link ? (
                <Link
                  to={entityKeyData.link}
                  title={entityKeyData.linkLabel}
                  aria-label={entityKeyData.linkLabel}
                >
                  {entityKeyData.label}
                </Link>
              ) : (
                entityKeyData.label
              )}
              &nbsp;
              {auditLog.entityDescription?.trim() || entityKeyData.name}
            </SecondColumn>
          </VerticallyAlignedRow>
          {['USER_TASK', 'INCIDENT', 'VARIABLE'].includes(
            auditLog.entityType,
          ) && isValidProcessInstanceKey(auditLog.processInstanceKey) ? (
            <VerticallyAlignedRow>
              <FirstColumn noWrap>
                <IconText>
                  <ParentNode />
                  Parent entity
                </IconText>
              </FirstColumn>
              <SecondColumn>
                <Link
                  to={Paths.processInstance(auditLog.processInstanceKey)}
                  aria-label={`View process instance ${auditLog.processInstanceKey}`}
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
                Date
              </IconText>
            </FirstColumn>
            <SecondColumn>{formatDate(auditLog.timestamp)}</SecondColumn>
          </VerticallyAlignedRow>
          {auditLog.entityType === 'BATCH' && (
            <>
              <VerticallyAlignedRow>
                <TitleListCell>Applied to:</TitleListCell>
              </VerticallyAlignedRow>
              <VerticallyAlignedRow>
                <FirstColumn noWrap>
                  <IconText>
                    <BatchJob />
                    Multiple{' '}
                    {formatBatchTitle(auditLog.batchOperationType ?? undefined)}
                    <CodeSnippet type="inline">
                      {auditLog.batchOperationKey}
                    </CodeSnippet>
                  </IconText>
                </FirstColumn>
                <SecondColumn>
                  <IconText>
                    <Link
                      to={Paths.batchOperation(
                        auditLog.batchOperationKey ?? undefined,
                      )}
                      aria-label={`View batch operation ${auditLog.batchOperationKey}`}
                    >
                      View batch operation details
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
                <TitleListCell>Details:</TitleListCell>
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
export {DetailsModal};
