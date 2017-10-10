import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import {ViewsAreaReact, __set__, __ResetDependency__} from 'main/processDisplay/views/ViewsArea';
import React from 'react';
import {mount} from 'enzyme';
import sinon from 'sinon';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<ViewsArea>', () => {
  let isViewSelected;
  let areaComponent;
  let createDefinitionCases;

  beforeEach(() => {
    isViewSelected = sinon.spy();
    areaComponent = 'Component1';

    createDefinitionCases = sinon.stub().returns(<div></div>);
    __set__('createDefinitionCases', createDefinitionCases);

    mount(<ViewsAreaReact isViewSelected={isViewSelected} areaComponent={areaComponent} />);
  });

  afterEach(() => {
    __ResetDependency__('createDefinitionCases');
  });

  it('should create definition cases with correct component and should display function', () => {
    expect(createDefinitionCases.calledWith(areaComponent, isViewSelected)).to.eql(true);
  });
});
