/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isNil from 'lodash/isNil';
import {useProcessInstanceDeprecated} from '../processInstance/deprecated/useProcessInstanceDeprecated';
import type {ProcessInstanceEntity} from 'modules/types/operate';

const latestMigrationDateParser = (data: ProcessInstanceEntity) => {
  const migrateOperations = data.operations
    .filter((operation) => {
      return (
        operation.type === 'MIGRATE_PROCESS_INSTANCE' &&
        // this filters for operations with a completedDate
        !isNil(operation.completedDate)
      );
    })
    .sort(({completedDate: dateA}, {completedDate: dateB}) => {
      // It is safe use the a non-null assertion operator (!) for dateA and dateB here.
      // The value will never be null or undefined, because only operations with a
      // completedDate are filtered above.
      return new Date(dateA!).getTime() - new Date(dateB!).getTime();
    });

  if (migrateOperations !== undefined) {
    const lastMigrationDate =
      migrateOperations[migrateOperations.length - 1]?.completedDate;

    return lastMigrationDate ?? undefined;
  }

  return undefined;
};

const useLatestMigrationDate = () =>
  useProcessInstanceDeprecated<string | undefined>(latestMigrationDateParser);

export {useLatestMigrationDate};
