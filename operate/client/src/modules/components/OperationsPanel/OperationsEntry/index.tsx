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

import {formatDate} from 'modules/utils/date';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {useLoadingProgress} from './useLoadingProgress';
import {Container, Details, Title, Header, ProgressBar} from './styled';
import OperationEntryStatus from './OperationEntryStatus';
import {
  TrashCan,
  Error,
  Tools,
  RetryFailed,
  Edit,
  MigrateAlt,
} from '@carbon/react/icons';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {IS_BATCH_MOVE_MODIFICATION_ENABLED} from 'modules/feature-flags';

type OperationLabelType =
  | 'Edit'
  | 'Retry'
  | 'Cancel'
  | 'Modify'
  | 'Delete'
  | 'Migrate';

const TYPE_LABELS: Readonly<Record<OperationEntityType, OperationLabelType>> = {
  ADD_VARIABLE: 'Edit',
  UPDATE_VARIABLE: 'Edit',
  RESOLVE_INCIDENT: 'Retry',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  DELETE_PROCESS_INSTANCE: 'Delete',
  MODIFY_PROCESS_INSTANCE: 'Modify',
  DELETE_PROCESS_DEFINITION: 'Delete',
  DELETE_DECISION_DEFINITION: 'Delete',
  MIGRATE_PROCESS_INSTANCE: 'Migrate',
};

type Props = {
  operation: OperationEntity;
};

const OperationsEntry: React.FC<Props> = ({operation}) => {
  const {
    id,
    type,
    name,
    endDate,
    instancesCount,
    operationsTotalCount,
    operationsFinishedCount,
    failedOperationsCount,
    completedOperationsCount,
  } = operation;

  const {fakeProgressPercentage, isComplete} = useLoadingProgress({
    totalCount: operationsTotalCount,
    finishedCount: operationsFinishedCount,
  });

  const label = TYPE_LABELS[type];

  const isTypeDeleteProcessOrDecision = [
    'DELETE_PROCESS_DEFINITION',
    'DELETE_DECISION_DEFINITION',
  ].includes(type);

  const shouldHaveIdLink =
    label !== 'Delete' ||
    (isTypeDeleteProcessOrDecision && failedOperationsCount) ||
    (label === 'Delete' &&
      !isTypeDeleteProcessOrDecision &&
      failedOperationsCount);

  return (
    <Container data-testid="operations-entry">
      <Header>
        <Title>
          {label}
          {isTypeDeleteProcessOrDecision ? ` ${name}` : ''}
        </Title>
        {label === 'Delete' && (
          <TrashCan size={16} data-testid="operation-delete-icon" />
        )}
        {label === 'Cancel' && (
          <Error size={16} data-testid="operation-cancel-icon" />
        )}
        {label === 'Retry' && (
          <RetryFailed size={16} data-testid="operation-retry-icon" />
        )}
        {label === 'Modify' && (
          <Tools size={16} data-testid="operation-modify-icon" />
        )}
        {label === 'Edit' && (
          <Edit size={16} data-testid="operation-edit-icon" />
        )}
        {label === 'Migrate' && (
          <MigrateAlt size={16} data-testid="operation-migrate-icon" />
        )}
      </Header>
      {IS_BATCH_MOVE_MODIFICATION_ENABLED && shouldHaveIdLink ? (
        <Link
          data-testid="operation-id"
          to={{
            pathname: Paths.processes(),
            search: `?active=true&incidents=true&completed=true&canceled=true&operationId=${id}`,
          }}
          state={{hideOptionalFilters: true}}
          onClick={panelStatesStore.expandFiltersPanel}
        >
          {id}
        </Link>
      ) : (
        <div data-testid="operation-id">{id}</div>
      )}
      {!isComplete && <ProgressBar label="" value={fakeProgressPercentage} />}
      <Details>
        {IS_BATCH_MOVE_MODIFICATION_ENABLED && (
          <OperationEntryStatus
            isTypeDeleteProcessOrDecision={isTypeDeleteProcessOrDecision}
            label={label}
            failedOperationsCount={failedOperationsCount}
            completedOperationsCount={completedOperationsCount}
          />
        )}

        {!IS_BATCH_MOVE_MODIFICATION_ENABLED && label !== 'Delete' && (
          <Link
            to={{
              pathname: Paths.processes(),
              search: `?active=true&incidents=true&completed=true&canceled=true&operationId=${id}`,
            }}
            state={{hideOptionalFilters: true}}
            onClick={panelStatesStore.expandFiltersPanel}
          >
            {`${pluralSuffix(instancesCount, 'Instance')}`}
          </Link>
        )}

        {!IS_BATCH_MOVE_MODIFICATION_ENABLED &&
          isTypeDeleteProcessOrDecision && (
            <div>{`${pluralSuffix(instancesCount, 'instance')} deleted`}</div>
          )}

        {endDate !== null && isComplete && <div>{formatDate(endDate)}</div>}
      </Details>
    </Container>
  );
};

export default OperationsEntry;
export type {OperationLabelType};
