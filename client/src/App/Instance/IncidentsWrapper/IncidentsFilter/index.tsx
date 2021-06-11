/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Pill from 'modules/components/Pill';

import Dropdown from 'modules/components/Dropdown';
import * as Styled from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';

const splitArray = (arr: any, size = 5) => {
  return [[...arr.slice(0, size)], [...arr.slice(size)]];
};

type Props = {
  selectedErrorTypes?: string[];
  selectedFlowNodes?: string[];
  onFlowNodeSelect: (...args: unknown[]) => void;
  onErrorTypeSelect: (...args: unknown[]) => void;
  onClearAll: () => void;
};

const IncidentsFilter: React.FC<Props> = observer(function IncidentsFilter({
  selectedErrorTypes,
  selectedFlowNodes,
  onErrorTypeSelect,
  onFlowNodeSelect,
  onClearAll,
}) {
  const {flowNodes, errorTypes} = incidentsStore;

  const groupedFlowNodes = splitArray([...flowNodes.values()]);
  const groupedErrorTypes = splitArray([...errorTypes.values()]);

  return (
    <Styled.FiltersWrapper>
      <Styled.Content data-testid="incidents-filter">
        <Styled.PillsWrapper>
          <Styled.FilterRow>
            <Styled.Label>Incident type:</Styled.Label>
            <Styled.Ul data-testid="incidents-by-errorType">
              {groupedErrorTypes[0].map((item) => {
                return (
                  <li key={item.errorType}>
                    <Pill
                      data-testid={item.errorType}
                      type="FILTER"
                      count={item.count}
                      isActive={selectedErrorTypes?.includes(item.errorType)}
                      onClick={() => onErrorTypeSelect(item.errorType)}
                    >
                      {item.errorType}
                    </Pill>
                  </li>
                );
              })}
              {Boolean(groupedErrorTypes[1].length) && (
                <li>
                  <Styled.MoreDropdown
                    label={`${groupedErrorTypes[1].length} more`}
                  >
                    {groupedErrorTypes[1].map((item) => {
                      return (
                        <Dropdown.Option key={item.errorType}>
                          <Pill
                            data-testid={item.errorType}
                            type="FILTER"
                            count={item.count}
                            isActive={selectedErrorTypes?.includes(
                              item.errorType
                            )}
                            onClick={() => onErrorTypeSelect(item.errorType)}
                            grow
                          >
                            {item.errorType}
                          </Pill>
                        </Dropdown.Option>
                      );
                    })}
                  </Styled.MoreDropdown>
                </li>
              )}
            </Styled.Ul>
          </Styled.FilterRow>
          <Styled.FilterRow>
            <Styled.Label>Flow Node:</Styled.Label>
            <Styled.Ul data-testid="incidents-by-flowNode">
              {groupedFlowNodes[0].map((item) => {
                return (
                  <li key={item.flowNodeId}>
                    <Pill
                      data-testid={item.flowNodeId}
                      type="FILTER"
                      count={item.count}
                      isActive={selectedFlowNodes?.includes(item.flowNodeId)}
                      onClick={() => onFlowNodeSelect(item.flowNodeId)}
                    >
                      {item.flowNodeName}
                    </Pill>
                  </li>
                );
              })}
              {Boolean(groupedFlowNodes[1].length) && (
                <li>
                  <Styled.MoreDropdown
                    label={`${groupedFlowNodes[1].length} more`}
                  >
                    {groupedFlowNodes[1].map((item) => {
                      return (
                        <Dropdown.Option key={item.flowNodeId}>
                          <Pill
                            type="FILTER"
                            count={item.count}
                            isActive={selectedFlowNodes?.includes(
                              item.flowNodeId
                            )}
                            grow={true}
                            onClick={() => onFlowNodeSelect(item.flowNodeId)}
                          >
                            {item.flowNodeName}
                          </Pill>
                        </Dropdown.Option>
                      );
                    })}
                  </Styled.MoreDropdown>
                </li>
              )}
            </Styled.Ul>
          </Styled.FilterRow>
        </Styled.PillsWrapper>
        <Styled.ButtonWrapper>
          <Styled.ClearButton
            data-testid="clear-button"
            size="small"
            title="Clear All"
            onClick={onClearAll}
            disabled={
              selectedFlowNodes?.length === 0 &&
              selectedErrorTypes?.length === 0
            }
          >
            Clear All
          </Styled.ClearButton>
        </Styled.ButtonWrapper>
      </Styled.Content>
    </Styled.FiltersWrapper>
  );
});

export {IncidentsFilter};
