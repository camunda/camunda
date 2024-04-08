/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import React, {useMemo} from 'react';
import {Row, Label, TaskLink, Stack, Container, DateLabel} from './styled';
import {pages} from 'modules/routing';
import {formatDate} from 'modules/utils/formatDate';
import {CurrentUser, Task as TaskType} from 'modules/types';
import {useLocation, useMatch} from 'react-router-dom';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {BodyCompact} from 'modules/components/FontTokens';
import {encodeTaskOpenedRef} from 'modules/utils/reftags';
import {AssigneeTag} from 'Tasks/AssigneeTag';
import {
  Calendar,
  CheckmarkFilled,
  Warning,
  Notification,
} from '@carbon/icons-react';
import {compareAsc} from 'date-fns';
import {unraw} from 'modules/utils/unraw';

type Props = {
  taskId: TaskType['id'];
  name: TaskType['name'];
  processName: TaskType['processName'];
  context: TaskType['context'];
  assignee: TaskType['assignee'];
  creationDate: TaskType['creationDate'];
  followUpDate: TaskType['followUpDate'];
  dueDate: TaskType['dueDate'];
  completionDate: TaskType['completionDate'];
  currentUser: CurrentUser;
  position: number;
};

const Task = React.forwardRef<HTMLElement, Props>(
  (
    {
      taskId,
      name,
      processName,
      context,
      assignee,
      creationDate,
      followUpDate,
      dueDate,
      completionDate,
      currentUser,
      position,
    },
    ref,
  ) => {
    const {userId} = currentUser;
    const isAssigned = assignee !== null;
    const isAssignedToCurrentUser = assignee === userId;
    const match = useMatch('/:id');
    const location = useLocation();
    const isActive = match?.params?.id === taskId;
    const {filter, sortBy} = useTaskFilters();
    const hasCompletionDate =
      completionDate !== null && formatDate(completionDate) !== '';
    const hasDueDate = dueDate !== null && formatDate(dueDate) !== '';
    const hasFollowupDate =
      followUpDate !== null && formatDate(followUpDate) !== '';
    const todaysDate = new Date().toISOString();
    const isOverdue =
      !hasCompletionDate &&
      hasDueDate &&
      compareAsc(dueDate, todaysDate) === -1;
    const isFollowupBeforeDueDate =
      hasDueDate && hasFollowupDate && compareAsc(dueDate, followUpDate) === 1;

    const showCompletionDate =
      hasCompletionDate && sortBy !== 'due' && sortBy !== 'follow-up';
    const showFollowupDate =
      !showCompletionDate &&
      hasFollowupDate &&
      sortBy !== 'due' &&
      sortBy !== 'completion' &&
      (sortBy === 'follow-up' || (!isOverdue && isFollowupBeforeDueDate));
    const showDueDate =
      !showCompletionDate &&
      !showFollowupDate &&
      hasDueDate &&
      sortBy !== 'follow-up';

    const searchWithRefTag = useMemo(() => {
      const params = new URLSearchParams(location.search);
      params.set(
        'ref',
        encodeTaskOpenedRef({
          by: 'user',
          position,
          filter,
          sorting: sortBy,
        }),
      );
      return params;
    }, [location, position, filter, sortBy]);

    const decodedContext = useMemo(
      () => (context !== null ? unraw(context) : null),
      [context],
    );

    return (
      <Container $active={isActive}>
        <TaskLink
          to={{
            ...location,
            pathname: pages.taskDetails(taskId),
            search: searchWithRefTag.toString(),
          }}
          aria-label={
            isAssigned
              ? `${
                  isAssignedToCurrentUser
                    ? `Task assigned to me`
                    : 'Assigned task'
                }: ${name}`
              : `Unassigned task: ${name}`
          }
        >
          <Stack data-testid={`task-${taskId}`} gap={3} ref={ref}>
            <Row>
              <BodyCompact $variant="02">{name}</BodyCompact>
              <Label $variant="secondary">{processName}</Label>
            </Row>

            {decodedContext === null ? null : (
              <Row>
                <Label $variant="secondary" $shouldWrap title={decodedContext}>
                  {decodedContext.split('\n').map((line, index) => (
                    <div key={index}>{line}</div>
                  ))}
                </Label>
              </Row>
            )}

            <Row>
              <Label $variant="secondary">
                <AssigneeTag currentUser={currentUser} assignee={assignee} />
              </Label>
            </Row>
            <Row data-testid="creation-time" $direction="row">
              {formatDate(creationDate) === '' ? null : (
                <DateLabel
                  $variant="primary"
                  title={`Created at ${formatDate(creationDate)}`}
                >
                  <Stack orientation="vertical" gap={1}>
                    <Label $variant="secondary">Created</Label>
                    <Stack orientation="horizontal" gap={2}>
                      <Calendar />
                      {formatDate(creationDate)}
                    </Stack>
                  </Stack>
                </DateLabel>
              )}
              {showFollowupDate ? (
                <DateLabel
                  $variant="primary"
                  title={`Follow-up at ${formatDate(followUpDate!, false)}`}
                >
                  <Stack orientation="vertical" gap={1}>
                    <Label $variant="secondary">Follow-up</Label>
                    <Stack orientation="horizontal" gap={2}>
                      <Notification color="blue" />
                      {formatDate(followUpDate!, false)}
                    </Stack>
                  </Stack>
                </DateLabel>
              ) : null}
              {showDueDate ? (
                <DateLabel
                  $variant="primary"
                  title={
                    isOverdue
                      ? `Overdue at ${formatDate(dueDate!, false)}`
                      : `Due at ${formatDate(dueDate!, false)}`
                  }
                >
                  <Stack orientation="vertical" gap={1}>
                    {isOverdue ? (
                      <>
                        <Label $variant="secondary">Overdue</Label>
                        <Stack orientation="horizontal" gap={2}>
                          <Warning color="red" />
                          {formatDate(dueDate!, false)}
                        </Stack>
                      </>
                    ) : (
                      <>
                        <Label $variant="secondary">Due</Label>
                        {formatDate(dueDate!, false)}
                      </>
                    )}
                  </Stack>
                </DateLabel>
              ) : null}
              {showCompletionDate ? (
                <DateLabel
                  $variant="primary"
                  title={`Completed at ${formatDate(completionDate!, false)}`}
                >
                  <Stack orientation="vertical" gap={1}>
                    <Label $variant="secondary">Completed</Label>
                    <Stack orientation="horizontal" gap={2}>
                      <CheckmarkFilled color="green" />
                      {formatDate(completionDate!, false)}
                    </Stack>
                  </Stack>
                </DateLabel>
              ) : null}
            </Row>
          </Stack>
        </TaskLink>
      </Container>
    );
  },
);

export {Task};
