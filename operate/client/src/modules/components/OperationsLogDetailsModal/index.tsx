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
  StructuredListCell,
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
} from '@carbon/react/icons';
import {
  TitleListCell,
  FirstColumn,
  IconText,
  ParagraphWithIcon,
  VerticallyAlignedRow,
  IconTextWithTopMargin,
} from './styled';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {OperationsLogStateIcon} from 'modules/components/OperationsLogStateIcon';
import {
  formatBatchTitle,
  formatModalHeading,
  getActorIcon,
} from 'modules/utils/operationsLog';
import {Paths} from 'modules/Routes';
import {useMemo} from 'react';
import {Snippet} from '../Snippet';
import AiAgentIcon from 'modules/components/Icon/ai-agent.svg?react';

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
            <StructuredListCell>
              <IconText>
                <OperationsLogStateIcon
                  state={auditLog.result}
                  data-testid={`${auditLog.auditLogKey}-icon`}
                />
                {spaceAndCapitalize(auditLog.result.toString())}
              </IconText>
            </StructuredListCell>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn>
              <IconText>
                <UserAvatar />
                Actor
              </IconText>
            </FirstColumn>
            <StructuredListCell>
              {!ActorIcon ? (
                auditLog.actorId
              ) : (
                <IconText>
                  <ActorIcon />
                  <Snippet hideCopyButton wrapText>
                    {auditLog.actorId}
                  </Snippet>
                </IconText>
              )}
              {auditLog.agentElementId && (
                <IconTextWithTopMargin>
                  <AiAgentIcon />
                  <Snippet hideCopyButton wrapText>
                    {auditLog.agentElementId}
                  </Snippet>
                </IconTextWithTopMargin>
              )}
            </StructuredListCell>
          </VerticallyAlignedRow>
          <VerticallyAlignedRow>
            <FirstColumn noWrap>
              <IconText>
                <EventSchedule />
                Date
              </IconText>
            </FirstColumn>
            <StructuredListCell>
              {formatDate(auditLog.timestamp)}
            </StructuredListCell>
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
                    Multiple {formatBatchTitle(auditLog.batchOperationType)}
                    <CodeSnippet type="inline">
                      {auditLog.batchOperationKey}
                    </CodeSnippet>
                  </IconText>
                </FirstColumn>
                <StructuredListCell>
                  <IconText>
                    <Link
                      to={Paths.batchOperation(auditLog.batchOperationKey)}
                      aria-label={`View batch operation ${auditLog.batchOperationKey}`}
                    >
                      View batch operation details
                      <ArrowRight />
                    </Link>
                  </IconText>
                </StructuredListCell>
              </VerticallyAlignedRow>
            </>
          )}
        </StructuredListBody>
      </StructuredListWrapper>
    </Modal>
  );
};

export type {DetailsModalState};
export {DetailsModal};
