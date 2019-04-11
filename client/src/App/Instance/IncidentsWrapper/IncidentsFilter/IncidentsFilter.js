/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import Pill from 'modules/components/Pill';

import Dropdown from 'modules/components/Dropdown';
import * as Styled from './styled';

const splitArray = (arr, size = 5) => {
  return [[...arr.slice(0, size)], [...arr.slice(size)]];
};

export default class IncidentsFilter extends React.Component {
  render() {
    const {
      flowNodes,
      errorTypes,
      selectedErrorTypes,
      selectedFlowNodes
    } = this.props;
    const groupedFlowNodes = splitArray(flowNodes);
    const groupedErrorTypes = splitArray(errorTypes);

    return (
      <Styled.FiltersWrapper>
        <Styled.Content>
          <Styled.PillsWrapper>
            <Styled.FilterRow>
              <Styled.Label>Incident type:</Styled.Label>
              <Styled.Ul data-test="incidents-by-errorType">
                {groupedErrorTypes[0].map(item => {
                  return (
                    <li key={item.errorType}>
                      <Pill
                        data-test={item.errorType}
                        type="FILTER"
                        count={item.count}
                        isActive={selectedErrorTypes.includes(item.errorType)}
                        onClick={this.props.onErrorTypeSelect.bind(
                          this,
                          item.errorType
                        )}
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
                      {groupedErrorTypes[1].map(item => {
                        return (
                          <Dropdown.Option key={item.errorType}>
                            <Pill
                              data-test={item.errorType}
                              type="FILTER"
                              count={item.count}
                              isActive={selectedErrorTypes.includes(
                                item.errorType
                              )}
                              onClick={this.props.onErrorTypeSelect.bind(
                                this,
                                item.errorType
                              )}
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
              <Styled.Ul data-test="incidents-by-flowNode">
                {groupedFlowNodes[0].map(item => {
                  return (
                    <li key={item.flowNodeId}>
                      <Pill
                        data-test={item.flowNodeId}
                        type="FILTER"
                        count={item.count}
                        isActive={selectedFlowNodes.includes(item.flowNodeId)}
                        onClick={this.props.onFlowNodeSelect.bind(
                          this,
                          item.flowNodeId
                        )}
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
                      {groupedFlowNodes[1].map(item => {
                        return (
                          <Dropdown.Option key={item.flowNodeId}>
                            <Pill
                              type="FILTER"
                              count={item.count}
                              isActive={selectedFlowNodes.includes(
                                item.flowNodeId
                              )}
                              grow={true}
                              onClick={this.props.onFlowNodeSelect.bind(
                                this,
                                item.flowNodeId
                              )}
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
              data-test="clear-button"
              size="small"
              title="Clear All"
              onClick={this.props.onClearAll}
              disabled={
                selectedFlowNodes.length === 0 &&
                selectedErrorTypes.length === 0
              }
            >
              Clear All
            </Styled.ClearButton>
          </Styled.ButtonWrapper>
        </Styled.Content>
      </Styled.FiltersWrapper>
    );
  }
}

IncidentsFilter.propTypes = {
  errorTypes: PropTypes.arrayOf(
    PropTypes.shape({
      errorType: PropTypes.string,
      errorTypeTitle: PropTypes.string,
      count: PropTypes.number
    })
  ),
  selectedErrorTypes: PropTypes.arrayOf(PropTypes.string),
  flowNodes: PropTypes.arrayOf(
    PropTypes.shape({
      flowNodeId: PropTypes.string,
      count: PropTypes.number
    })
  ),
  selectedFlowNodes: PropTypes.arrayOf(PropTypes.string),
  onFlowNodeSelect: PropTypes.func.isRequired,
  onErrorTypeSelect: PropTypes.func.isRequired,
  onClearAll: PropTypes.func.isRequired
};
