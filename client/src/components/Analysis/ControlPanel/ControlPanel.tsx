/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Children, ReactNode, useCallback, useEffect, useState} from 'react';
import {Layer} from '@carbon/react';
import update, {Spec} from 'immutability-helper';

// @ts-expect-error no types available
import {Filter} from 'filter';
import {loadVariables} from 'services';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';
import {ProcessFilter, Variable} from 'types';

import './ControlPanel.scss';

interface ControlPanelProps {
  children?: ReactNode;
  filters: ProcessFilter[];
  processDefinitionKey: string;
  processDefinitionVersions: string[];
  tenantIds: string[];
  onChange: (args: {filters: ProcessFilter[]}) => void;
}

export function ControlPanel({
  children,
  filters,
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  onChange,
}: ControlPanelProps) {
  const [variables, setVariables] = useState<Variable[]>([]);
  const {mightFail} = useErrorHandling();

  const definitions = [];
  if (processDefinitionKey) {
    definitions.push({
      identifier: 'definition',
      key: processDefinitionKey,
      versions: processDefinitionVersions,
      tenantIds: tenantIds,
      name: processDefinitionKey,
      displayName: processDefinitionKey,
    });
  }

  const fetchVariables = useCallback(
    function () {
      if (processDefinitionKey && processDefinitionVersions) {
        mightFail(
          loadVariables([{processDefinitionKey, processDefinitionVersions, tenantIds}]),
          setVariables,
          showError
        );
      }
    },
    [processDefinitionKey, processDefinitionVersions, tenantIds, mightFail]
  );

  useEffect(() => {
    fetchVariables();
  }, [fetchVariables]);
  return (
    <Layer as="ul" level={2} className="ControlPanel">
      {Children.map(children, (child, index) => (
        <li key={index}>{child}</li>
      ))}
      <li className="filtersListItem">
        <Filter
          data={filters}
          onChange={({filter: updatedFilter}: {filter: Spec<ProcessFilter[]>}) =>
            onChange({filters: update(filters, updatedFilter)})
          }
          definitions={definitions}
          filterLevel="instance"
          variables={variables}
        />
      </li>
    </Layer>
  );
}
