/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef} from 'react';
import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';
import Viewer from 'dmn-js';

import {AdditionalModule, DMNDiagram} from './DMNDiagram';

jest.mock('dmn-js', () =>
  jest.fn().mockImplementation(() => ({
    destroy: jest.fn(),
    importXML: jest.fn(),
    open: jest.fn(),
    getViews: jest.fn().mockReturnValue([
      {type: 'DMNDiagram', element: {id: 'a'}},
      {type: 'DMNDiagram', element: {id: 'key'}},
      {type: 'DMNDiagram', element: {id: 'c'}},
    ]),
  }))
);

jest.mock('react', () => {
  const outstandingEffects: Function[] = [];
  const viewer = {current: {destroy: jest.fn()}};
  const useRef = () => viewer;
  useRef.viewer = viewer;
  return {
    ...jest.requireActual('react'),
    useEffect: (fn: Function) => outstandingEffects.push(fn),
    runLastEffect: () => {
      if (outstandingEffects.length) {
        outstandingEffects.pop()?.();
      }
    },
    useRef,
  };
});

jest.mock('@bpmn-io/dmn-migrate', () => ({migrateDiagram: (xml: string) => xml}));

const props = {
  xml: 'dmn xml string',
  decisionDefinitionKey: 'key',
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should construct a new Viewer instance with any addons provided', () => {
  shallow(<DMNDiagram {...props} additionalModules={['test' as unknown as AdditionalModule]} />);
  runLastEffect();

  expect((Viewer as jest.Mock).mock.calls[0][0].decisionTable.additionalModules).toEqual(['test']);
});

it('should import the provided xml', async () => {
  shallow(<DMNDiagram {...props} />);
  await flushPromises();
  runLastEffect();

  expect((useRef as any).viewer.current.importXML).toHaveBeenCalled();
  expect((useRef as any).viewer.current.importXML.mock.calls[0][0]).toBe('dmn xml string');
});

it('invoke onLoad after openning the diagram', async () => {
  const spy = jest.fn();
  shallow(<DMNDiagram {...props} onLoad={spy} />);
  runLastEffect();
  await flushPromises();

  expect((useRef as any).viewer.current.open).toHaveBeenCalled();
  expect(spy).toHaveBeenCalled();
});

it('should render children', () => {
  const node = shallow(
    <DMNDiagram {...props}>
      <div className="childContent" />
    </DMNDiagram>
  );

  expect(node.find('.childContent')).toExist();
});
