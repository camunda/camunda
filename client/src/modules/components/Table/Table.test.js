import React from 'react';
import {shallow} from 'enzyme';

import Table from './Table';

const data = [
  {
    prop1: 1,
    prop2: 2,
    prop3: 3
  },
  {
    prop1: 4,
    prop2: 5,
    prop3: 6
  }
];

const headerLabels = {
  prop1: 'Property 1',
  prop2: 'Property 2',
  prop3: 'Property 3'
};

const order = ['prop2', 'prop3', 'prop1'];

describe('Table', () => {
  it('should display the data passed as props', () => {
    const node = shallow(<Table data={data} />);

    expect(node).toMatchSnapshot();
  });

  it('should display a header if provided', () => {
    const node = shallow(<Table data={data} config={{headerLabels}} />);

    expect(node).toMatchSnapshot();
  });

  it('should sort the header and body according to an optional order config', () => {
    const node = shallow(<Table data={data} config={{order, headerLabels}} />);

    expect(node).toMatchSnapshot();
  });

  it('should not crash if data array is empty', () => {
    const node = shallow(<Table data={[]} />);

    expect(node).toMatchSnapshot();
  });

  it('should accept arbitrary markup for header and body cells', () => {
    const bodySpy = jest.fn();
    const headerSpy = jest.fn();
    const node = shallow(
      <Table
        data={[
          {
            prop: (
              <button data-test-id="body-button" onClick={bodySpy}>
                Body
              </button>
            )
          }
        ]}
        config={{
          headerLabels: {
            prop: (
              <button data-test-id="header-button" onClick={headerSpy}>
                Header
              </button>
            )
          }
        }}
      />
    );

    expect(node).toMatchSnapshot();
    node.find('[data-test-id="body-button"]').simulate('click');
    expect(bodySpy).toHaveBeenCalled();
    node.find('[data-test-id="header-button"]').simulate('click');
    expect(headerSpy).toHaveBeenCalled();
  });

  it('should pass the selected attribute to a body row', () => {
    const node = shallow(
      <Table data={data} config={{selectionCheck: () => true}} />
    );

    expect(node).toMatchSnapshot();
  });
});
