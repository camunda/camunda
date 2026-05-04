/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, InlineLoading} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import {observer} from 'mobx-react';
import {useExportProcessInstances} from 'modules/mutations/processes/useExportProcessInstances';
import {
  useProcessInstancesSearchFilter,
  useProcessInstancesSearchSort,
} from 'modules/hooks/processInstancesSearch';
import {variableFilterStore} from 'modules/stores/variableFilter';

type Props = {
  totalCount: number;
};

const ExportButton: React.FC<Props> = observer(({totalCount}) => {
  const filter = useProcessInstancesSearchFilter(variableFilterStore.variable);
  const sort = useProcessInstancesSearchSort();
  const {mutate, isPending} = useExportProcessInstances();

  const disabled = isPending || filter === undefined || totalCount === 0;

  return (
    <Button
      kind="ghost"
      size="sm"
      disabled={disabled}
      renderIcon={isPending ? undefined : Download}
      onClick={() => {
        if (filter !== undefined) {
          mutate({filter, sort});
        }
      }}
      data-testid="export-process-instances-csv"
    >
      {isPending ? (
        <InlineLoading description="Exporting..." />
      ) : (
        'Export to CSV'
      )}
    </Button>
  );
});

export {ExportButton};
