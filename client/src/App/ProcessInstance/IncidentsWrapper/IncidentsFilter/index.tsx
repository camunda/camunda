/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import Pill from 'modules/components/Pill';
import Option from 'modules/components/Dropdown/Option';
import * as Styled from './styled';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {tracking} from 'modules/tracking';
import {Button} from 'modules/components/Button';

const IncidentsFilter: React.FC = observer(function IncidentsFilter() {
  const {
    flowNodes,
    errorTypes,
    toggleErrorTypeSelection,
    toggleFlowNodeSelection,
    clearSelection,
    state: {selectedErrorTypes, selectedFlowNodes},
  } = incidentsStore;

  const moreErrorTypes = errorTypes.slice(5);
  const moreFlowNodes = flowNodes.slice(5);

  return (
    <Styled.FiltersWrapper>
      <Styled.Content data-testid="incidents-filter">
        <Styled.PillsWrapper>
          <Styled.FilterRow>
            <Styled.Label>Incident type:</Styled.Label>
            <Styled.Ul data-testid="incidents-by-errorType">
              {errorTypes.slice(0, 4).map(({id, count, name}) => {
                return (
                  <li key={id}>
                    <Pill
                      data-testid={id}
                      type="FILTER"
                      count={count}
                      isActive={selectedErrorTypes.includes(id)}
                      onClick={() => toggleErrorTypeSelection(id)}
                    >
                      {name}
                    </Pill>
                  </li>
                );
              })}
              {moreErrorTypes.length > 0 && (
                <li>
                  <Styled.MoreDropdown label={`${moreErrorTypes.length} more`}>
                    {moreErrorTypes.map(({id, count, name}) => {
                      return (
                        <Option key={id}>
                          <Pill
                            data-testid={id}
                            type="FILTER"
                            count={count}
                            isActive={selectedErrorTypes.includes(id)}
                            onClick={() => toggleErrorTypeSelection(id)}
                            grow
                          >
                            {name}
                          </Pill>
                        </Option>
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
              {flowNodes.slice(0, 4).map(({id, name, count}) => {
                return (
                  <li key={id}>
                    <Pill
                      data-testid={id}
                      type="FILTER"
                      count={count}
                      isActive={selectedFlowNodes.includes(id)}
                      onClick={() => {
                        toggleFlowNodeSelection(id);
                        tracking.track({
                          eventName: 'incident-filtered',
                        });
                      }}
                    >
                      {name}
                    </Pill>
                  </li>
                );
              })}
              {moreFlowNodes.length > 0 && (
                <li>
                  <Styled.MoreDropdown label={`${moreFlowNodes.length} more`}>
                    {moreFlowNodes.map(({id, name, count}) => {
                      return (
                        <Option key={id}>
                          <Pill
                            type="FILTER"
                            count={count}
                            isActive={selectedFlowNodes.includes(id)}
                            grow={true}
                            onClick={() => toggleFlowNodeSelection(id)}
                          >
                            {name}
                          </Pill>
                        </Option>
                      );
                    })}
                  </Styled.MoreDropdown>
                </li>
              )}
            </Styled.Ul>
          </Styled.FilterRow>
        </Styled.PillsWrapper>
        <Styled.ButtonWrapper>
          <Button
            data-testid="clear-button"
            size="small"
            title="Clear All"
            onClick={() => {
              clearSelection();
              tracking.track({
                eventName: 'incident-filters-cleared',
              });
            }}
            disabled={
              selectedFlowNodes.length === 0 && selectedErrorTypes.length === 0
            }
            type="button"
          >
            Clear All
          </Button>
        </Styled.ButtonWrapper>
      </Styled.Content>
    </Styled.FiltersWrapper>
  );
});

export {IncidentsFilter};
