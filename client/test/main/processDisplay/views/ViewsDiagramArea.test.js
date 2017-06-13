import {jsx, isTruthy, isFalsy, Case} from 'view-utils';
import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {ViewsDiagramArea, __set__, __ResetDependency__} from 'main/processDisplay/views/ViewsDiagramArea';

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
  let update;
  let node;

  beforeEach(() => {
    LoadingIndicator = createMockComponent('LoadingIndicator', true);
    __set__('LoadingIndicator', LoadingIndicator);

    Diagram = createMockComponent('Diagram');
    createDiagram = sinon.stub().returns(Diagram);
    __set__('createDiagram', createDiagram);

    isLoading = isFalsy;
    __set__('isLoading', isLoading);

    isLoaded = isTruthy;
    __set__('isLoaded', isLoaded);

    createDefinitionCases = sinon.stub().returns(<Case predicate={isFalsy} />);
    __set__('createDefinitionCases', createDefinitionCases);

    isViewSelected = sinon.stub().returns(true);

    getView = sinon.stub().returns('view');
    __set__('getView', getView);

    definitions = {
      view: {
        hasNoData: ({data}) => !data
      }
    };
    __set__('definitions', definitions);

    ({node, update} = mountTemplate(<ViewsDiagramArea isViewSelected={isViewSelected}/>));
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
    expect(createDefinitionCases.calledWith('Diagram', isViewSelected)).to.eql(true);
  });

  it('should display no data indidactor when heatmap is loaded and it has no data', () => {
    const state = {
      data: false
    };

    update(state);

    expect(node).to.contain.text('No Data');
  });

  it('should not display no data indicator when heatmap is loaded and has data', () => {
    const state = {
      data: true
    };

    update(state);

    expect(node).not.to.contain.text('No Data');
  });
});
