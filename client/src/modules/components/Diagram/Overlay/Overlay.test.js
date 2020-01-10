/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {mount} from 'enzyme';

import Overlay from './Overlay';

const mockProps = {
  onOverlayAdd: jest.fn(),
  onOverlayClear: jest.fn(),
  isViewerLoaded: true,
  id: 'someId',
  type: 'someType',
  position: {top: 20, left: 20}
};

const Child = () => <div>Child</div>;

const mountNode = (customProps = {}) => {
  return mount(
    <Overlay {...mockProps} {...customProps}>
      <Child />
    </Overlay>
  );
};

const mountNodeInContainer = (customProps = {}) => {
  const Container = ({removeChild = false}) => (
    <div>
      {removeChild ? null : (
        <Overlay {...mockProps} {...customProps}>
          <Child />
        </Overlay>
      )}
    </div>
  );

  Container.propTypes = {removeChild: PropTypes.bool};

  return mount(<Container />);
};

// test overlay behavior
describe('Overlay', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render overlay', () => {
    // given
    const node = mountNode();

    // then
    expect(node.find('Child')).toHaveLength(1);
  });

  it('should add overlay on mount if the viewer is loaded', () => {
    // given
    mountNode();
    const expectedHtml = mount(<Child />).html();

    // then
    expect(mockProps.onOverlayAdd).toBeCalled();
    const args = mockProps.onOverlayAdd.mock.calls[0];
    expect(args[0]).toBe(mockProps.id);
    expect(args[1]).toBe(mockProps.type);
    expect(args[2].position).toEqual(mockProps.position);
    expect(args[2].html.innerHTML).toEqual(expectedHtml);
  });

  it('should not add overlay on mount if the viewer is not loaded', () => {
    // given
    mountNode({isViewerLoaded: false});

    // then
    expect(mockProps.onOverlayAdd).not.toBeCalled();
  });

  it('should clear overlay when component unmounts', () => {
    // given
    const node = mountNodeInContainer();

    // when
    node.setProps({removeChild: true});

    // then
    expect(mockProps.onOverlayClear).toBeCalled();
  });

  it("should reattach to diagram when the viewer's load changes", () => {
    // given
    const node = mountNode({isViewerLoaded: false});
    mockProps.onOverlayAdd.mockClear();
    mockProps.onOverlayClear.mockClear();

    // when
    node.setProps({isViewerLoaded: true});

    // then
    expect(mockProps.onOverlayClear).toBeCalled();
    expect(mockProps.onOverlayAdd).toBeCalled();
  });

  it('should reattach to diagram when id changes', () => {
    // given
    const node = mountNode({isViewerLoaded: true});
    mockProps.onOverlayAdd.mockClear();
    mockProps.onOverlayClear.mockClear();

    // when
    node.setProps({id: 'anotherId'});

    // then
    expect(mockProps.onOverlayClear).toBeCalled();
    expect(mockProps.onOverlayAdd).toBeCalled();
  });
});
