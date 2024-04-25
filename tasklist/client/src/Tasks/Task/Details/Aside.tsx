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

import {ContainedList, ContainedListItem, Tag} from '@carbon/react';
import {formatDate} from 'modules/utils/formatDate';
import styles from './Aside.module.scss';
import {CurrentUser, Task} from 'modules/types';

type Props = {
  task: Task;
  user: CurrentUser;
};

const Aside: React.FC<Props> = ({task, user}) => {
  const {
    creationDate,
    completionDate,
    dueDate,
    followUpDate,
    candidateUsers,
    candidateGroups,
    tenantId,
  } = task;
  const taskTenant =
    user.tenants.length > 1
      ? user.tenants.find((tenant) => tenant.id === tenantId)
      : undefined;
  const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];

  return (
    <aside className={styles.aside} aria-label="Task details right panel">
      <ContainedList label="Details" kind="disclosed">
        <>
          {taskTenant === undefined ? null : (
            <ContainedListItem>
              <span className={styles.itemHeading}>Tenant</span>
              <br />
              <span className={styles.itemBody}>{taskTenant.name}</span>
            </ContainedListItem>
          )}
        </>
        <ContainedListItem>
          <span className={styles.itemHeading}>Creation date</span>
          <br />
          <span className={styles.itemBody}>{formatDate(creationDate)}</span>
        </ContainedListItem>
        <ContainedListItem>
          <span className={styles.itemHeading}>Candidates</span>
          <br />
          {candidates.length === 0 ? (
            <span className={styles.itemBody}>No candidates</span>
          ) : null}
          {candidates.map((candidate) => (
            <Tag size="sm" type="gray" key={candidate}>
              {candidate}
            </Tag>
          ))}
        </ContainedListItem>
        {completionDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>Completion date</span>
            <br />
            <span className={styles.itemBody}>
              {formatDate(completionDate)}
            </span>
          </ContainedListItem>
        ) : null}
        <ContainedListItem>
          <span className={styles.itemHeading}>Due date</span>
          <br />
          <span className={styles.itemBody}>
            {dueDate ? formatDate(dueDate) : 'No due date'}
          </span>
        </ContainedListItem>
        {followUpDate ? (
          <ContainedListItem>
            <span className={styles.itemHeading}>Follow up date</span>
            <br />
            <span className={styles.itemBody}>{formatDate(followUpDate)}</span>
          </ContainedListItem>
        ) : null}
      </ContainedList>
    </aside>
  );
};

export {Aside};
