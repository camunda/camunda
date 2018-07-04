import React from 'react';
import {shallow} from 'enzyme';
import withStrippedProps from './withStrippedProps';

describe('withStrippedProps', () => {
  it('remove given props from a component', () => {
    // given
    const testProps = {
      onClick: () => {},
      test: 'blue',
      className: 'main'
    };

    // when
    const Component = props => <div {...props} />;
    const StrippedComponent = withStrippedProps(['test', 'onClick'])(Component);
    const node = shallow(<StrippedComponent {...testProps} />);

    // then
    expect(node.props('test')).not.toBe(undefined);
    expect(node.props('onClick')).not.toBe(undefined);
  });
});
