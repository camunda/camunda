import React from 'react';
import {shallow} from 'enzyme';

import Footer from './ListFooter';

describe('ListFooter', () => {
  it('should not show a pagination if there is only one page', () => {
    const node = shallow(
      <Footer
        onFirstElementChange={jest.fn()}
        perPage={10}
        firstElement={0}
        total={6}
      />
    );

    expect(node).toMatchSnapshot();
  });

  it('should show the first five pages when on first page', () => {
    const node = shallow(
      <Footer
        onFirstElementChange={jest.fn()}
        perPage={10}
        firstElement={0}
        total={100}
      />
    );

    expect(node).toMatchSnapshot();
  });

  it('should show the last five pages when on last page', () => {
    const node = shallow(
      <Footer
        onFirstElementChange={jest.fn()}
        perPage={10}
        firstElement={94}
        total={100}
      />
    );

    expect(node).toMatchSnapshot();
  });

  it('should show ellipsis on both sides in the middle', () => {
    const node = shallow(
      <Footer
        onFirstElementChange={jest.fn()}
        perPage={5}
        firstElement={50}
        total={100}
      />
    );

    expect(node).toMatchSnapshot();
  });

  it('should not show ellipsis when first page is just barely out of range', () => {
    const node = shallow(
      <Footer
        onFirstElementChange={jest.fn()}
        perPage={10}
        firstElement={30}
        total={100}
      />
    );

    expect(node).toMatchSnapshot();
  });
});
