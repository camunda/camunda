import {jsx, Default} from 'view-utils';
import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate} from 'testHelpers';
import {ViewsArea, __set__, __ResetDependency__} from 'main/processDisplay/views/ViewsArea';

describe('<ViewsArea>', () => {
  let isViewSelected;
  let areaComponent;
  let createDefinitionCases;

  beforeEach(() => {
    isViewSelected = sinon.spy();
    areaComponent = 'Component1';

    createDefinitionCases = sinon.stub().returns(<Default />);
    __set__('createDefinitionCases', createDefinitionCases);

    mountTemplate(<ViewsArea isViewSelected={isViewSelected} areaComponent={areaComponent} />);
  });

  afterEach(() => {
    __ResetDependency__('createDefinitionCases');
  });

  it('should create definition cases with correct component and should display function', () => {
    expect(createDefinitionCases.calledWith(areaComponent, isViewSelected)).to.eql(true);
  });
});
