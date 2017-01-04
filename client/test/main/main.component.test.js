import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {createMockComponent} from 'testHelpers/createMockComponent';
import {jsx} from 'view-utils';
import {Main, __set__, __ResetDependency__} from 'main/main.component';

describe('<Main>', () => {
  let node;

  beforeEach(() => {
    // Mocking components
    __set__('Header', createMockComponent('header-mock'));
    __set__('Footer', createMockComponent('footer-mock'));

    ({node} = mountTemplate(<Main />));
  });

  afterEach(() => {
    __ResetDependency__('Header');
    __ResetDependency__('Footer');
  });

  it('should include header', () => {
    expect(node).to.contain.text('header-mock');
  });

  it('should include footer', () => {
    expect(node).to.contain.text('footer-mock');
  });

  it('should have container element', () => {
    expect(node.querySelector('.container-fluid')).to.exist;
  });

  it('should have content element', () => {
    expect(node.querySelector('.content__views')).to.exist;
  });
});
