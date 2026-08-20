/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {observer} from 'mobx-react';
import {SelectItem, Stack, Tag, Toggle, Tooltip} from '@carbon/react';

import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processInstanceMigrationMappingStore} from 'modules/stores/processInstanceMigrationMapping';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {
  BottomSection,
  CheckmarkFilled,
  DataTable,
  ErrorMessageContainer,
  LeftColumn,
  IconContainer,
  Select,
  SourceElementName,
  ArrowRight,
  ToggleContainer,
  WarningFilled,
} from './styled';
import {useMigrationSourceXml} from 'modules/queries/processDefinitions/useMigrationSourceXml';
import {useMigrationTargetXml} from 'modules/queries/processDefinitions/useMigrationTargetXml';
import {hasEmbeddedForm} from 'modules/bpmn-js/utils/hasEmbeddedForm';
import {isCamundaUserTask} from 'modules/bpmn-js/utils/isCamundaUserTask';
import {EmbeddedFormWarningNotification} from './EmbeddedFormWarningNotification';

const TOGGLE_LABEL = 'Show only not mapped';

const BottomPanel: React.FC = observer(() => {
  const {selectedSourceElementIds} = processInstanceMigrationStore;

  const handleCheckIsRowSelected = (selectedSourceElements?: string[]) => {
    return (rowId: string) => selectedSourceElements?.includes(rowId) ?? false;
  };

  const {
    updateElementMapping,
    clearElementMapping,
    state: {elementMapping, sourceProcessDefinition, targetProcessDefinition},
  } = processInstanceMigrationStore;
  const isTargetSelected = !!targetProcessDefinition;

  const {
    toggleMappedFilter,
    state: {isMappedFilterEnabled},
    getMappableElements,
    getMappableSequenceFlows,
  } = processInstanceMigrationMappingStore;

  const {data: sourceData} = useMigrationSourceXml({
    processDefinitionKey: sourceProcessDefinition?.processDefinitionKey,
    processDefinitionId: sourceProcessDefinition?.processDefinitionId,
  });

  const {data: targetData} = useMigrationTargetXml({
    processDefinitionKey: targetProcessDefinition?.processDefinitionKey,
    processDefinitionId: targetProcessDefinition?.processDefinitionId,
  });

  const mappableElements = getMappableElements(
    sourceData?.selectableElements,
    targetData?.selectableElements,
  );

  const mappableSequenceFlows =
    getMappableSequenceFlows(
      sourceData?.selectableSequenceFlows,
      targetData?.selectableSequenceFlows,
    ) ?? [];

  const filteredSourceElements = [
    ...mappableElements,
    ...mappableSequenceFlows,
  ].filter(({sourceElement}) => {
    return (
      !isMappedFilterEnabled || elementMapping[sourceElement.id] === undefined
    );
  });

  /**
   * Elements (elements and sequence flows) which are contained in both source diagram and target diagram.
   *
   * An element is auto-mappable when
   * - the element id is contained in source and target diagram
   * - the bpmn type is matching in source and target diagram
   */
  const autoMappableElements = useMemo(() => {
    if (sourceData === undefined || targetData === undefined) {
      return [];
    }

    return [
      ...sourceData.selectableElements,
      ...sourceData.selectableSequenceFlows,
    ]
      .filter((sourceElement) => {
        const targetElement = [
          ...targetData.selectableElements,
          ...targetData.selectableSequenceFlows,
        ].find((element) => element.id === sourceElement.id);

        return (
          targetElement !== undefined &&
          sourceElement.$type === targetElement.$type
        );
      })
      .map(({id, $type}) => {
        return {id, type: $type};
      });
  }, [sourceData, targetData]);

  /**
   * Returns true if an element with elementId is auto-mappable
   */
  const isAutoMappable = (elementId: string) => {
    return (
      autoMappableElements.find(({id}) => {
        return elementId === id;
      }) !== undefined
    );
  };

  const hasSelectableSourceElements =
    sourceData?.selectableElements && sourceData.selectableElements.length > 0;

  const hasSelectableSourceSequenceFlows =
    sourceData?.selectableSequenceFlows &&
    sourceData.selectableSequenceFlows.length > 0;

  /**
   * Returns true if sourceElement and targetElement is migration from embedded form to Camunda user task
   */
  const isEmbeddedFormMigration = (
    sourceElement: string,
    targetElement: string | undefined,
  ) => {
    const sourceBusinessObject = sourceData?.selectableElements.find(
      (element) => element.id === sourceElement,
    );
    const targetBusinessObject = targetData?.selectableElements.find(
      (element) => element.id === targetElement,
    );
    return (
      isTargetSelected &&
      hasEmbeddedForm(sourceBusinessObject) &&
      isCamundaUserTask(targetBusinessObject)
    );
  };

  /**
   * Returns true if any of the mapped elements is migration from embedded form to Camunda user task
   */
  const hasAnyEmbeddedFormMigration = () => {
    return Object.entries(elementMapping).some(([sourceId, targetId]) =>
      isEmbeddedFormMigration(sourceId, targetId),
    );
  };

  // Automatically map elements with same id and type in source and target diagrams
  useEffect(() => {
    clearElementMapping();
    autoMappableElements.forEach((sourceElement) => {
      updateElementMapping({
        sourceId: sourceElement.id,
        targetId: sourceElement.id,
      });
    });
  }, [autoMappableElements, updateElementMapping, clearElementMapping]);
  useEffect(() => {
    // reset store on unmount
    return processInstanceMigrationMappingStore.reset;
  }, []);

  return (
    <BottomSection>
      {!hasSelectableSourceElements && !hasSelectableSourceSequenceFlows ? (
        <ErrorMessageContainer>
          <ErrorMessage
            message="There are no mappable elements or sequence flows."
            additionalInfo="Exit migration to select a different process"
          />
        </ErrorMessageContainer>
      ) : (
        <>
          {isTargetSelected && (
            <ToggleContainer>
              <Toggle
                size="sm"
                id="not-mapped-toggle"
                labelA={TOGGLE_LABEL}
                labelB={TOGGLE_LABEL}
                aria-label={TOGGLE_LABEL}
                onToggle={toggleMappedFilter}
              />
              <ArrowRight />
            </ToggleContainer>
          )}
          <DataTable
            size="md"
            headers={[
              {
                header: 'Source elements',
                key: 'sourceElement',
                width: '50%',
              },
              {
                header: 'Target elements',
                key: 'targetElement',
                width: '50%',
              },
            ]}
            onRowClick={(rowId) => {
              processInstanceMigrationStore.selectSourceElement(rowId);
            }}
            checkIsRowSelected={handleCheckIsRowSelected(
              selectedSourceElementIds,
            )}
            rows={filteredSourceElements.map(
              ({sourceElement, selectableTargetElement}) => {
                const isMapped = elementMapping[sourceElement.id] !== undefined;

                return {
                  id: sourceElement.id,
                  sourceElement: (
                    <LeftColumn>
                      {isEmbeddedFormMigration(
                        sourceElement.id,
                        elementMapping[sourceElement.id] ?? '',
                      ) && (
                        <Tooltip
                          label={
                            'Embedded form in this user task will be replaced by the form defined in the target element.'
                          }
                          align="right-bottom"
                          leaveDelayMs={50}
                        >
                          <WarningFilled />
                        </Tooltip>
                      )}
                      <SourceElementName>
                        {sourceElement.name ?? sourceElement.id}
                      </SourceElementName>
                      {isTargetSelected && !isMapped && (
                        <Tag type="blue">Not mapped</Tag>
                      )}
                      <ArrowRight />
                    </LeftColumn>
                  ),
                  targetElement: (() => {
                    const targetElementId =
                      elementMapping[sourceElement.id] ?? '';

                    return (
                      <Stack orientation="horizontal" gap={4}>
                        <Select
                          disabled={
                            processInstanceMigrationStore.state.currentStep ===
                              'summary' || selectableTargetElement.length === 0
                          }
                          size="sm"
                          hideLabel
                          labelText={`Target element for ${sourceElement.name}`}
                          id={sourceElement.id}
                          value={targetElementId}
                          onChange={({target}) => {
                            processInstanceMigrationStore.updateElementMapping({
                              sourceId: sourceElement.id,
                              targetId: target.value,
                            });
                          }}
                        >
                          {[{id: '', name: ''}, ...selectableTargetElement].map(
                            ({id, name}) => {
                              return (
                                <SelectItem
                                  key={id}
                                  value={id}
                                  text={name ?? id}
                                />
                              );
                            },
                          )}
                        </Select>
                        {isAutoMappable(sourceElement.id) &&
                          // show icon only when target element is selected
                          sourceElement.id === targetElementId && (
                            <IconContainer title="This element was automatically mapped">
                              <CheckmarkFilled data-testid="select-icon" />
                            </IconContainer>
                          )}
                      </Stack>
                    );
                  })(),
                };
              },
            )}
          />
        </>
      )}
      {isTargetSelected && hasAnyEmbeddedFormMigration() && (
        <EmbeddedFormWarningNotification />
      )}
    </BottomSection>
  );
});

export {BottomPanel};
