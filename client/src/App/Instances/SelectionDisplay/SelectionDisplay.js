import React from 'react';

import * as Styled from './styled';

export default class SelectionDisplay extends React.Component {
  render() {
    return (
      <Styled.DebugView>
        {JSON.stringify(
          this.props,
          (k, v) => (v instanceof Set ? [...v] : v),
          1
        )}
      </Styled.DebugView>
    );
  }
}
