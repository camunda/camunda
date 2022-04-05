/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render} from '@testing-library/react';

import {Overlay} from './index';

const mockProps = {
  onOverlayAdd: jest.fn(),
  onOverlayClear: jest.fn(),
  isViewerLoaded: true,
  id: 'someId',
  type: 'someType',
  position: {top: 20, left: 20},
};

const Child = () => <div>Child</div>;

const mountNode = (customProps = {}) => {
  return render(
    <Overlay {...mockProps} {...customProps}>
      <Child />
    </Overlay>
  );
};

type ContainerProps = {
  removeChild?: boolean;
};
const Container: React.FC<ContainerProps> = ({removeChild = false}) => (
  <div>
    {removeChild ? null : (
      <Overlay {...mockProps}>
        <Child />
      </Overlay>
    )}
  </div>
);

// test overlay behavior
describe('Overlay', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should add overlay on mount if the viewer is loaded', () => {
    // given
    mountNode();
    render(<Child />);

    // then
    expect(mockProps.onOverlayAdd).toBeCalled();
    const args = mockProps.onOverlayAdd.mock.calls[0];
    expect(args[0]).toBe(mockProps.id);
    expect(args[1]).toBe(mockProps.type);
    expect(args[2].position).toEqual(mockProps.position);
  });

  it('should not add overlay on mount if the viewer is not loaded', () => {
    // given
    mountNode({isViewerLoaded: false});

    // then
    expect(mockProps.onOverlayAdd).not.toBeCalled();
  });

  it('should clear overlay when component unmounts', () => {
    // given
    const {rerender} = render(<Container />);

    // when
    rerender(<Container removeChild={true} />);

    // then
    expect(mockProps.onOverlayClear).toBeCalled();
  });

  it("should reattach to diagram when the viewer's load changes", () => {
    // given
    const {rerender} = mountNode({isViewerLoaded: false});
    mockProps.onOverlayAdd.mockClear();
    mockProps.onOverlayClear.mockClear();

    // when
    rerender(
      <Overlay {...mockProps} isViewerLoaded={true}>
        <Child />
      </Overlay>
    );

    // then
    expect(mockProps.onOverlayClear).toBeCalled();
    expect(mockProps.onOverlayAdd).toBeCalled();
  });

  it('should reattach to diagram when id changes', () => {
    // given
    const {rerender} = mountNode({isViewerLoaded: true});
    mockProps.onOverlayAdd.mockClear();
    mockProps.onOverlayClear.mockClear();

    // when
    rerender(
      <Overlay {...mockProps} id={'anotherId'}>
        <Child />
      </Overlay>
    );

    // then
    expect(mockProps.onOverlayClear).toBeCalled();
    expect(mockProps.onOverlayAdd).toBeCalled();
  });
});
