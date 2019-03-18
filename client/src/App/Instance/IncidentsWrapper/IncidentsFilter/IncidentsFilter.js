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

export default function IncidentsFilter({flowNodes, errorTypes}) {
  const groupedFlowNodes = splitArray(flowNodes);
  const groupedErrorTypes = splitArray(errorTypes);
  return (
    <Styled.FiltersWrapper>
      <Styled.Content>
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
                    isActive={false}
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
                          isActive={false}
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
                  <Pill type="FILTER" count={item.count} isActive={false}>
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
                          isActive={false}
                          grow={true}
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
      </Styled.Content>
    </Styled.FiltersWrapper>
  );
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
