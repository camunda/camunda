import React from 'react';
import PropTypes from 'prop-types';

import {EMPTY_PLACEHOLDER} from './constants';
import * as Styled from './styled';

export default class InstancePayload extends React.Component {
  static propTypes = {
    payload: PropTypes.object
  };

  render() {
    return (
      <Styled.InstancePayload>
        <Styled.PayloadContent>
          {!this.props.payload ? (
            <Styled.Placeholder>{EMPTY_PLACEHOLDER}</Styled.Placeholder>
          ) : (
            <Styled.Table>
              <thead>
                <Styled.TR>
                  <Styled.TH>Key</Styled.TH>
                  <Styled.TH>Value</Styled.TH>
                </Styled.TR>
              </thead>
              <tbody>
                {Object.entries(this.props.payload).map(([key, value]) => (
                  <Styled.TR key={key}>
                    <Styled.TD isBold={true}>{key}</Styled.TD>
                    <Styled.TD>{JSON.stringify(value)}</Styled.TD>
                  </Styled.TR>
                ))}
              </tbody>
            </Styled.Table>
          )}
        </Styled.PayloadContent>
      </Styled.InstancePayload>
    );
  }
}
