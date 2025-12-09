/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {observer} from 'mobx-react';
import {formatDate} from 'modules/utils/date';
import {useAuditLogs} from 'modules/queries/auditLog/useAuditLogs';
import {
  type AuditLog,
  type QueryAuditLogsRequestBody,
  auditLogSortFieldEnum,
} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {Container, OperationLogName} from './styled';
import {SortableTable} from 'modules/components/SortableTable';
import {getSortParams} from 'modules/utils/filter';
import {useLocation} from 'react-router-dom';
import {Information} from '@carbon/react/icons';
import {Button} from '@carbon/react';
import {notificationsStore} from 'modules/stores/notifications';
import {logger} from 'modules/logger';
import {tracking} from 'modules/tracking';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {AuditLogIcon} from './AuditLogIcon';

type Props = {
  flowNodeInstanceId?: string | undefined;
  isVisible: boolean;
};

const OperationsLog: React.FC<Props> = observer(
  ({flowNodeInstanceId, isVisible}: Props) => {
    const location = useLocation();
    const sortParams = getSortParams(location.search) || {
      sortBy: 'timestamp',
      sortOrder: 'desc',
    };
    const sortByParsed = auditLogSortFieldEnum.safeParse(sortParams.sortBy);
    const sortBy = sortByParsed.success ? sortByParsed.data : 'timestamp';

    const request: QueryAuditLogsRequestBody = useMemo(
      () => ({
        sort: [
          {
            field: sortBy,
            order: sortParams.sortOrder,
          },
        ],
        filter: {
          elementInstanceKey: flowNodeInstanceId,
        },
        page: {
          from: 0,
          limit: 100,
        },
      }),
      [flowNodeInstanceId, sortBy, sortParams.sortOrder],
    );

    const {data, isLoading, error} = useAuditLogs(request, {
      enabled: isVisible,
    });

    useEffect(() => {
      if (error !== null) {
        tracking.track({
          eventName: 'audit-logs-fetch-failed',
        });
        notificationsStore.displayNotification({
          isDismissable: true,
          kind: 'error',
          title: 'Audit logs could not be fetched',
        });
        logger.error(error);
      }
    }, [error]);

    const rows = useMemo(
      () =>
        data?.items.map((item: AuditLog) => ({
          id: item.auditLogKey,
          operationType: `${spaceAndCapitalize(item.operationType.toString())} ${spaceAndCapitalize(
            item.entityType.toString(),
          )}`,
          result: (
            <OperationLogName>
              <AuditLogIcon
                state={item.result}
                data-testid={`${item.auditLogKey}-icon`}
              />
              {spaceAndCapitalize(item.result.toString())}
            </OperationLogName>
          ),
          user: item.actorId,
          timestamp: formatDate(item.timestamp),
          comment: (
            <Button
              kind="ghost"
              size="sm"
              tooltipPosition="left"
              iconDescription="Open details"
              aria-label="Open details"
              hasIconOnly
              renderIcon={Information}
            />
          ),
        })) || [],
      [data],
    );

    const getTableState = () => {
      if (!isVisible) {
        return 'skeleton';
      } else if (isLoading) {
        return 'loading';
      } else if (error) {
        return 'error';
      } else if (rows.length === 0) {
        return 'empty';
      }
      return 'content';
    };

    return (
      <Container>
        <SortableTable
          state={getTableState()}
          rows={rows}
          emptyMessage={{
            message: 'No operations found for this instance',
          }}
          headerColumns={[
            {
              header: 'Operation',
              key: 'operationType',
              sortKey: 'operationType',
            },
            {
              header: 'Status',
              key: 'result',
            },
            {
              header: 'Actor',
              key: 'user',
              sortKey: 'actorId',
            },
            {
              header: 'Time',
              key: 'timestamp',
              sortKey: 'timestamp',
            },
            {
              header: '',
              key: 'comment',
              isDisabled: true,
            },
          ]}
        />
      </Container>
    );
  },
);

export {OperationsLog};
