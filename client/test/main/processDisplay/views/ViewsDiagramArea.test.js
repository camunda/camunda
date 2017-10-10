import {isTruthy, isFalsy} from 'view-utils';
import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {ViewsDiagramAreaReact, __set__, __ResetDependency__} from 'main/processDisplay/views/ViewsDiagramArea';
import React from 'react';
import {mount} from 'enzyme';
import sinon from 'sinon';
import {createReactMock} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<ViewsDiagramArea>', () => {
  let LoadingIndicator;
  let Diagram;
  let createDiagram;
  let isLoading;
  let isLoaded;
  let createDefinitionCases;
  let isViewSelected;
  let getView;
  let definitions;
  let node;

  beforeEach(() => {
    LoadingIndicator = createReactMock('LoadingIndicator', true);
    __set__('LoadingIndicator', LoadingIndicator);

    Diagram = createReactMock('Diagram');
    createDiagram = sinon.stub().returns(Diagram);
    __set__('createDiagram', createDiagram);

    isLoading = isFalsy;
    __set__('isLoading', isLoading);

    isLoaded = isTruthy;
    __set__('isLoaded', isLoaded);

    createDefinitionCases = sinon.stub().returns(<div></div>);
    __set__('createDefinitionCases', createDefinitionCases);

    isViewSelected = sinon.stub().returns(true);

    getView = sinon.stub().returns('view');
    __set__('getView', getView);

    definitions = {
      view: {
        hasNoData: ({data}) => !data
      },
      data: true
    };
    __set__('definitions', definitions);

    node = mount(<ViewsDiagramAreaReact views={definitions} isViewSelected={isViewSelected}/>);
  });

  afterEach(() => {
    __ResetDependency__('LoadingIndicator');
    __ResetDependency__('createDiagram');
    __ResetDependency__('isLoading');
    __ResetDependency__('isLoaded');
    __ResetDependency__('createDefinitionCases');
    __ResetDependency__('getView');
    __ResetDependency__('definitions');
  });

  it('should create cases for definitions diagrams', () => {
    expect(createDefinitionCases.calledWith('Diagram', isViewSelected, definitions)).to.eql(true);
  });

  it('should display no data indidactor when heatmap is loaded and it has no data', () => {
    definitions.data = false;
    node.setProps({views: definitions});

    expect(node).to.contain.text('No Data');
  });

  it('should not display no data indicator when heatmap is loaded and has data', () => {
    expect(node).not.to.contain.text('No Data');
  });
});
