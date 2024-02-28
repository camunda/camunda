/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatDate} from 'modules/utils/date';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {useLoadingProgress} from './useLoadingProgress';
import {Container, Details, Title, Header, ProgressBar} from './styled';
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
  } = operation;

  const {fakeProgressPercentage, isComplete} = useLoadingProgress({
    totalCount: operationsTotalCount,
    finishedCount: operationsFinishedCount,
  });

  const label = TYPE_LABELS[type];

  return (
    <Container data-testid="operations-entry">
      <Header>
        <Title>
          {label}
          {['DELETE_PROCESS_DEFINITION', 'DELETE_DECISION_DEFINITION'].includes(
            type,
          )
            ? ` ${name}`
            : ''}
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
      <div data-testid="operation-id">{id}</div>
      {!isComplete && <ProgressBar label="" value={fakeProgressPercentage} />}
      <Details>
        {label !== 'Delete' && (
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

        {['DELETE_PROCESS_DEFINITION', 'DELETE_DECISION_DEFINITION'].includes(
          type,
        ) && <div>{`${pluralSuffix(instancesCount, 'instance')} deleted`}</div>}

        {endDate !== null && isComplete && <div>{formatDate(endDate)}</div>}
      </Details>
    </Container>
  );
};

export default OperationsEntry;
