/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef, useState} from 'react';
import {useForm, useFormState} from 'react-final-form';
import {Button, Search} from '@carbon/react';
import {Edit} from '@carbon/react/icons';
import {
  StructuredList,
  VariableName,
  FilterSwitcherContainer,
  FilterSwitcher,
  FilterSwitcherButton,
  EmptyMessageWrapper,
  DimmableResults,
  VariablesSearch,
} from './styled';
import {StructuredRows} from 'modules/components/StructuredList';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {OnLastVariableModificationRemoved} from './OnLastVariableModificationRemoved';
import {FieldArray} from 'react-final-form-arrays';
import {Operations} from './Operations';
import type {VariableFormValues} from 'modules/types/variables';
import {EditButtons} from './EditButtons';
import {ExistingVariableValue} from './ExistingVariableValue';
import {Name} from './NewVariableModification/Name';
import {Value} from './NewVariableModification/Value';
import {Operation} from './NewVariableModification/Operation';
import {ViewFullVariableButton} from './ViewFullVariableButton';
import {useIsProcessInstanceRunning} from 'modules/queries/processInstance/useIsProcessInstanceRunning';
import {useVariables} from 'modules/queries/variables/useVariables';
import {useDebouncedValue} from 'modules/hooks/useDebouncedValue';
import {VariableValueCell} from './VariableValueCell';
import {parseDocumentVariable} from './DocumentValueCell/parseDocumentVariable';
import {DownloadDocumentButton} from './DownloadDocumentButton';
import {PreviewDocumentButton} from './DocumentPreview/PreviewDocumentButton';
import {ViewDocumentListButton} from './DocumentList/ViewDocumentListButton';

type Props = {
  scopeId: string | null;
  isModificationModeEnabled?: boolean;
  isVariableModificationAllowed?: boolean;
};

const VariablesTable: React.FC<Props> = ({
  scopeId,
  isModificationModeEnabled,
  isVariableModificationAllowed,
}) => {
  const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();
  const {initialValues} = useFormState<VariableFormValues>();
  const form = useForm<VariableFormValues>();
  const variableNameRef = useRef<HTMLDivElement>(null);
  const [showDocumentsOnly, setShowDocumentsOnly] = useState(false);
  const [searchValue, setSearchValue] = useState('');
  const debouncedSearchValue = useDebouncedValue(searchValue);

  const {
    data: variablesData,
    fetchNextPage,
    hasNextPage,
    isFetching,
    isPlaceholderData,
    isFetchingNextPage,
  } = useVariables({
    documentsOnly: showDocumentsOnly,
    keepPreviousResults: true,
    searchTerm: debouncedSearchValue,
  });

  const processedVariables = useMemo(() => {
    const allVariables =
      variablesData?.pages.flatMap((page) => page.items) ?? [];

    return allVariables
      .map((variable) => ({
        name: variable.name,
        value: variable.value,
        variableKey: variable.variableKey,
        isTruncated: Boolean(variable.isTruncated),
        documentResult: parseDocumentVariable(
          variable.value,
          Boolean(variable.isTruncated),
        ),
      }))
      .filter((variable) =>
        showDocumentsOnly ? variable.documentResult !== null : true,
      );
  }, [variablesData, showDocumentsOnly]);

  const isEditMode = (variableName: string) =>
    (initialValues?.name === variableName && isProcessInstanceRunning) ||
    isVariableModificationAllowed;

  const rows = processedVariables.map(
    ({name, value, variableKey, isTruncated, documentResult}) => ({
      key: name,
      dataTestId: `variable-${name}`,
      columns: [
        {
          cellContent: (
            <VariableName title={name} ref={variableNameRef}>
              {name}
            </VariableName>
          ),
          width: '35%',
        },
        {
          cellContent: isEditMode(name) ? (
            <ExistingVariableValue
              id={variableKey}
              variableName={name}
              variableValue={value}
              // TODO #46571: Verify if it's really optional
              isPreview={Boolean(isTruncated)}
            />
          ) : (
            <VariableValueCell
              variableKey={variableKey}
              variableName={name}
              value={value}
              documentResult={documentResult}
              isTruncated={isTruncated}
              isModificationModeEnabled={isModificationModeEnabled}
              isProcessInstanceRunning={isProcessInstanceRunning}
            />
          ),
          width: 'auto',
        },
        {
          cellContent: (
            <Operations>
              <ViewFullVariableButton
                variableName={name}
                variableKey={variableKey}
                variableValue={value}
                mode={isEditMode(name) ? 'edit' : 'show'}
                canEdit={
                  !isModificationModeEnabled && !!isProcessInstanceRunning
                }
              />
              {(() => {
                if (documentResult !== null) {
                  return (
                    <>
                      {documentResult.type === 'single' && (
                        <>
                          <PreviewDocumentButton
                            document={documentResult.document}
                            variableName={name}
                          />
                          <DownloadDocumentButton
                            document={documentResult.document}
                            variableName={name}
                          />
                        </>
                      )}
                      {documentResult.type === 'list' && (
                        <ViewDocumentListButton
                          documents={documentResult.documents}
                          isLowerBound={documentResult.isLowerBound}
                          variableKey={variableKey}
                          variableName={name}
                        />
                      )}
                    </>
                  );
                }

                if (isModificationModeEnabled || !isProcessInstanceRunning) {
                  return null;
                }

                if (initialValues?.name === name) {
                  return <EditButtons />;
                }

                return (
                  <Button
                    kind="ghost"
                    size="sm"
                    tooltipPosition="top"
                    iconDescription="Edit"
                    aria-label={`Edit variable ${name}`}
                    disabled={isFetchingNextPage || form.getState().submitting}
                    onClick={async () => {
                      form.reset({name, value, variableKey});
                      form.change('value', value);
                    }}
                    hasIconOnly
                    renderIcon={Edit}
                  />
                );
              })()}
            </Operations>
          ),
          width: '120px',
        },
      ],
    }),
  );

  return (
    <>
      {!isModificationModeEnabled && (
        <FilterSwitcherContainer>
          <VariablesSearch>
            <Search
              size="sm"
              labelText="Search variables by name"
              placeholder="Search variables by name"
              value={searchValue}
              onChange={(event) => setSearchValue(event.target.value)}
              onClear={() => setSearchValue('')}
            />
          </VariablesSearch>
          <FilterSwitcher role="group" aria-label="Variable filter">
            <FilterSwitcherButton
              type="button"
              aria-pressed={!showDocumentsOnly}
              onClick={() => setShowDocumentsOnly(false)}
            >
              All
            </FilterSwitcherButton>
            <FilterSwitcherButton
              type="button"
              aria-pressed={showDocumentsOnly}
              onClick={() => setShowDocumentsOnly(true)}
            >
              Documents
            </FilterSwitcherButton>
          </FilterSwitcher>
        </FilterSwitcherContainer>
      )}
      {!isFetching &&
      (showDocumentsOnly || debouncedSearchValue.trim() !== '') &&
      processedVariables.length === 0 ? (
        <EmptyMessageWrapper>
          <EmptyMessage
            message={
              debouncedSearchValue.trim() !== ''
                ? 'No variables match your search'
                : 'There are no document variables'
            }
          />
        </EmptyMessageWrapper>
      ) : (
        <DimmableResults $dimmed={isPlaceholderData}>
          <StructuredList
            dataTestId="variables-list"
            headerColumns={[
              {cellContent: 'Name', width: '35%'},
              {cellContent: 'Value', width: 'auto'},
              {cellContent: '', width: '120px'},
            ]}
            headerSize="sm"
            verticalCellPadding="var(--cds-spacing-02)"
            label="Variable List"
            onVerticalScrollEndReach={() => {
              if (hasNextPage && !isFetchingNextPage) {
                fetchNextPage();
              }
            }}
            dynamicRows={
              isVariableModificationAllowed ? (
                <>
                  <OnLastVariableModificationRemoved />
                  <FieldArray name="newVariables">
                    {({fields}) => (
                      <StructuredRows
                        verticalCellPadding="var(--cds-spacing-02)"
                        rows={fields.map((variableName, index) => ({
                          key: fields.value[index]?.id ?? variableName,
                          dataTestId: `variable-${variableName}`,
                          columns: [
                            {
                              cellContent: (
                                <Name
                                  variableName={variableName}
                                  scopeId={scopeId}
                                />
                              ),
                              width: '35%',
                            },
                            {
                              cellContent: (
                                <Value
                                  variableName={variableName}
                                  scopeId={scopeId}
                                />
                              ),
                              width: 'auto',
                            },
                            {
                              cellContent: (
                                <Operations>
                                  <ViewFullVariableButton
                                    shouldSubmitOnApply={false}
                                    mode="add"
                                    scopeId={scopeId}
                                    variableName={variableName}
                                  />
                                  <Operation
                                    variableName={variableName}
                                    onRemove={() => {
                                      fields.remove(index);
                                    }}
                                  />
                                </Operations>
                              ),
                              width: '120px',
                            },
                          ],
                        }))}
                      />
                    )}
                  </FieldArray>
                </>
              ) : undefined
            }
            rows={rows}
          />
        </DimmableResults>
      )}
    </>
  );
};

export {VariablesTable};
