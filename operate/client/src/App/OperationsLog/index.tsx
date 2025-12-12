/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {useLocation} from 'react-router-dom';
import {useAuditLogs} from 'modules/queries/auditLog/useAuditLogs';
import {SortableTable} from 'modules/components/SortableTable';
import {Information} from '@carbon/react/icons';
import {Button, Stack} from '@carbon/react';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {
  type AuditLog,
  auditLogFilterSchema,
  auditLogSortFieldEnum,
  type QueryAuditLogsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {tracking} from 'modules/tracking';
import {notificationsStore} from 'modules/stores/notifications';
import {logger} from 'modules/logger';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {
  Container,
  OperationLogName,
} from '../ProcessInstance/BottomPanel/VariablePanel/OperationsLog/styled';
import {AuditLogIcon} from '../ProcessInstance/BottomPanel/VariablePanel/OperationsLog/AuditLogIcon';
import {Field, Form} from 'react-final-form';
import z from 'zod';
import {TextInputField} from 'modules/components/TextInputField';
import {Title} from 'modules/components/FiltersPanel/styled';
import {FiltersPanel} from 'modules/components/FiltersPanel/index';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';

const ROW_HEIGHT = 46;
const SMOOTH_SCROLL_STEP_SIZE = 5 * ROW_HEIGHT;

const OperationsLog: React.FC = () => {
  return (
    <Container
      style={{
        display: 'grid',
        height: '100%',
        position: 'relative',
        overflow: 'auto',
        gridTemplateColumns: 'auto minmax(0, 1fr)',
        gridTemplateRows: '1fr',
      }}
    >
      <OperationsFilter />
      <OperationsTable />
    </Container>
  );
};

type AuditLogFilter = z.infer<typeof auditLogFilterSchema>;

const OperationsFilter: React.FC = () => {
  return (
    <>
      <Form<AuditLogFilter>
        onSubmit={(_: AuditLogFilter) => {
          // TODO
        }}
        initialValues={{}}
      >
        {({handleSubmit, form, values}) => (
          <form onSubmit={handleSubmit} style={{height: '100%'}}>
            <FiltersPanel
              localStorageKey="isAuditLogsFiltersCollapsed"
              isResetButtonDisabled={false}
              onResetClick={() => {
                // TODO
              }}
            >
              <Container style={{width: '100%', padding: '1rem'}}>
                <Stack gap={5}>
                  <div>
                    <Title>Process</Title>
                    <Stack gap={5}>
                      <Field name="Name">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="name"
                            size="sm"
                            labelText="Name"
                            type="text"
                            placeholder="Name"
                            autoFocus={true}
                          />
                        )}
                      </Field>
                      <Field name="Version">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="name"
                            size="sm"
                            labelText="Version"
                            type="text"
                            placeholder="Name"
                            autoFocus={true}
                          />
                        )}
                      </Field>
                      <Field name="Version">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="name"
                            size="sm"
                            labelText="Process instance key"
                            type="text"
                            placeholder="Name"
                            autoFocus={true}
                          />
                        )}
                      </Field>
                    </Stack>
                  </div>
                  <div>
                    <Title>Operation</Title>
                    <Stack gap={5}>
                      <Field name="Name">
                        {({input}) => (
                          <TextInputField
                            {...input}
                            id="name"
                            size="sm"
                            labelText="Name"
                            type="text"
                            placeholder="Name"
                            autoFocus={true}
                          />
                        )}
                      </Field>
                    </Stack>
                  </div>
                </Stack>
              </Container>
            </FiltersPanel>
          </form>
        )}
      </Form>
    </>
  );
};

const OperationsTable: React.FC = () => {
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
      filter: {},
    }),
    [sortBy, sortParams.sortOrder],
  );

  const {
    data,
    isLoading,
    error,
    isFetchingPreviousPage,
    hasPreviousPage,
    fetchPreviousPage,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
  } = useAuditLogs(request, {
    enabled: true,
    select: (data) => {
      tracking.track({
        eventName: 'audit-logs-loaded',
        filters: Object.keys(request.filter ?? {}),
        sort: request.sort,
      });
      return {
        auditLogs: data.pages.flatMap((page) => page.items),
        totalCount: data.pages.at(0)?.page.totalItems ?? 0,
      };
    },
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
      data?.auditLogs.map((item: AuditLog) => ({
        id: item.auditLogKey,
        operationType: `${spaceAndCapitalize(item.operationType.toString())}`,
        entityType: `${spaceAndCapitalize(item.entityType.toString())}`,
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
    if (isLoading) {
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
      <BasePanelHeader title="Operations Log" />
      <SortableTable
        state={getTableState()}
        rows={rows}
        emptyMessage={{
          message: 'No operations found for this instance',
        }}
        onVerticalScrollStartReach={async (scrollDown) => {
          if (hasPreviousPage && !isFetchingPreviousPage) {
            await fetchPreviousPage();
            scrollDown(SMOOTH_SCROLL_STEP_SIZE);
          }
        }}
        onVerticalScrollEndReach={() => {
          if (hasNextPage && !isFetchingNextPage) {
            fetchNextPage();
          }
        }}
        headerColumns={[
          {
            header: 'Operation',
            key: 'operationType',
            sortKey: 'operationType',
          },
          {
            header: 'Entity',
            key: 'entityType',
            sortKey: 'entityType',
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
};

export {OperationsLog};
