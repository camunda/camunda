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
  constructor(props) {
    super(props);
    const flowNodeIds = props.flowNodes.map(item => item.flowNodeId);
    const errorTypesList = props.errorTypes.map(item => item.errorType);

    this.state = {
      selectedFlowNodeIds: flowNodeIds,
      selectedErrorTypes: errorTypesList
    };
  }

  handleSelectedFlowNodeIds = id => {
    let index = this.state.selectedFlowNodeIds.findIndex(item => item === id);
    let list = [...this.state.selectedFlowNodeIds];
    if (index === -1) {
      list.push(id);
    } else {
      list.splice(index, 1);
    }

    this.setState({
      selectedFlowNodeIds: list
    });
  };

  handleSelectedErrorTypes = id => {
    let index = this.state.selectedErrorTypes.findIndex(item => item === id);
    let list = [...this.state.selectedErrorTypes];
    if (index === -1) {
      list.push(id);
    } else {
      list.splice(index, 1);
    }

    this.setState({
      selectedErrorTypes: list
    });
  };

  handleClearAll = () => {
    this.setState({
      selectedErrorTypes: [],
      selectedFlowNodeIds: []
    });
  };

  render() {
    const {flowNodes, errorTypes} = this.props;
    const groupedFlowNodes = splitArray(flowNodes);
    const groupedErrorTypes = splitArray(errorTypes);

    return (
      <Styled.FiltersWrapper>
        <Styled.Content>
          <Styled.PillsWrapper>
            <Styled.FilterRow>
              <Styled.Label>Incident type:</Styled.Label>
              <Styled.Ul>
                {groupedErrorTypes[0].map(item => {
                  return (
                    <li key={item.errorType}>
                      <Pill
                        data-test="incident-filter-pill"
                        type="FILTER"
                        count={item.count}
                        isActive={this.state.selectedErrorTypes.includes(
                          item.errorType
                        )}
                        onClick={this.handleSelectedErrorTypes.bind(
                          this,
                          item.errorType
                        )}
                      >
                        {item.errorTypeTitle}
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
                              data-test="incident-filter-pill"
                              type="FILTER"
                              count={item.count}
                              isActive={this.state.selectedErrorTypes.includes(
                                item.errorType
                              )}
                              onClick={this.handleSelectedErrorTypes.bind(
                                this,
                                item.errorType
                              )}
                              grow
                            >
                              {item.errorTypeTitle}
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
              <Styled.Ul>
                {groupedFlowNodes[0].map(item => {
                  return (
                    <li key={item.flowNodeId}>
                      <Pill
                        type="FILTER"
                        count={item.count}
                        isActive={this.state.selectedFlowNodeIds.includes(
                          item.flowNodeId
                        )}
                        onClick={this.handleSelectedFlowNodeIds.bind(
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
                              isActive={this.state.selectedFlowNodeIds.includes(
                                item.flowNodeId
                              )}
                              grow={true}
                              onClick={this.handleSelectedFlowNodeIds.bind(
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
              size="small"
              title="Clear All"
              onClick={this.handleClearAll}
              disabled={
                this.state.selectedFlowNodeIds.length === 0 &&
                this.state.selectedErrorTypes.length === 0
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
  flowNodes: PropTypes.arrayOf(
    PropTypes.shape({
      flowNodeId: PropTypes.string,
      count: PropTypes.number
    })
  )
};
