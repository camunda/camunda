import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {createDefinitionCases, __set__, __ResetDependency__} from 'main/processDisplay/views/createDefinitionCases';
import React from 'react';
import {mount} from 'enzyme';
import {createReactMock} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('main/processDisplay/views createDefinitionCases', () => {
  let node;
  let currentView;
  let shouldDisplay;
  let definitions;

  beforeEach(() => {
    shouldDisplay = view => view === currentView;

    definitions = [
      {
        id: 'd1',
        Component: createReactMock('d1')
      },
      {
        id: 'd2',
        Component: createReactMock('d2')
      }
    ];

    __set__('definitions', definitions);
  });

  afterEach(() => {
    __ResetDependency__('definitions');
  });

  it('should display d1 Component when d1 view is choosen', () => {
    currentView = 'd1';

    node = mount(<div>{
      createDefinitionCases('Component', shouldDisplay)
    }</div>);

    expect(node).to.contain.text(definitions[0].Component.text);
  });

  it('should display d2 Component when d2 view is choosen', () => {
    currentView = 'd2';

    node = mount(<div>{
      createDefinitionCases('Component', shouldDisplay)
    }</div>);

    expect(node).to.contain.text(definitions[1].Component.text);
  });
});
