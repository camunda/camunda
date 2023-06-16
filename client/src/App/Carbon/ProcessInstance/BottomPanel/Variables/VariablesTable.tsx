/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {StructuredList} from './styled';
import {StructuredRows} from 'modules/components/Carbon/StructuredList';
import {OnLastVariableModificationRemoved} from 'App/ProcessInstance/BottomPanel/Variables/OnLastVariableModificationRemoved';
import {FieldArray} from 'react-final-form-arrays';
import {variablesStore} from 'modules/stores/variables';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {useMemo} from 'react';

type Props = {
  scopeId: string | null;
};

const VariablesTable: React.FC<Props> = observer(({scopeId}) => {
  const {
    state: {items},
  } = variablesStore;
  const {isModificationModeEnabled} = modificationsStore;

  const addVariableModifications = useMemo(
    () => modificationsStore.getAddVariableModifications(scopeId),
    [scopeId]
  );

  return (
    <StructuredList
      headerColumns={[
        {cellContent: 'Name'},
        {cellContent: 'Value'},
        {cellContent: ''},
      ]}
      headerSize="sm"
      label="Variable List"
      dynamicRows={
        isModificationModeEnabled ? (
          <>
            <OnLastVariableModificationRemoved />
            <FieldArray
              name="newVariables"
              initialValue={
                addVariableModifications.length > 0
                  ? addVariableModifications
                  : undefined
              }
            >
              {({fields}) => (
                <StructuredRows
                  rows={fields
                    .map((_, index) => {
                      return {
                        columns: [
                          {
                            cellContent: <div>new variable name</div>,
                          },
                          {
                            cellContent: <div>new variable value</div>,
                          },
                          {
                            cellContent: (
                              <button
                                onClick={() => {
                                  fields.remove(index);
                                }}
                              >
                                remove new variable
                              </button>
                            ),
                          },
                        ],
                      };
                    })
                    .reverse()}
                />
              )}
            </FieldArray>
          </>
        ) : undefined
      }
      rows={items.map(({name: variableName, value: variableValue}) => ({
        columns: [
          {
            cellContent: variableName,
          },
          {
            cellContent: variableValue,
          },
          {
            cellContent: 'operations',
          },
        ],
      }))}
    />
  );
});

export {VariablesTable};
