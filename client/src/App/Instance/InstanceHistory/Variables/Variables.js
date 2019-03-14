/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {EMPTY_PLACEHOLDER, NULL_PLACEHOLDER} from './constants';
import * as Styled from './styled';

export default class Variables extends React.Component {
  static propTypes = {
    variables: PropTypes.array
  };

  render() {
    return (
      <Styled.Variables>
        <Styled.VariablesContent>
          {!this.props.variables || !this.props.variables.length ? (
            <Styled.Placeholder>
              {!this.props.variables ? NULL_PLACEHOLDER : EMPTY_PLACEHOLDER}
            </Styled.Placeholder>
          ) : (
            <Styled.Table>
              <thead>
                <Styled.TR>
                  <Styled.TH>Key</Styled.TH>
                  <Styled.TH>Value</Styled.TH>
                </Styled.TR>
              </thead>
              <tbody>
                {this.props.variables.map(({name, value}) => (
                  <Styled.TR key={name} data-test={name}>
                    <Styled.TD isBold={true}>{name}</Styled.TD>
                    <Styled.TD>{JSON.stringify(value)}</Styled.TD>
                  </Styled.TR>
                ))}
              </tbody>
            </Styled.Table>
          )}
        </Styled.VariablesContent>
      </Styled.Variables>
    );
  }
}
