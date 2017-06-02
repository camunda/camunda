import chaiDom from 'chai-dom';
import chai from 'chai';
import {setElementVisibility, isElementVisible} from 'view-utils';

chai.use(chaiDom);

const {expect} = chai;

describe('setElementVisibility', () => {
  let node;

  beforeEach(() => {
    node = document.createElement('div');
  });

  it('should add hidden class when element should not be visible', () => {
    setElementVisibility(node, false);

    expect(node).to.have.class('hidden');
  });

  it('should remove hidden class when element should be visible', () => {
    node.classList.add('hidden');

    setElementVisibility(node, true);

    expect(node).not.to.have.class('hidden');
  });
});

describe('isElementVisible', () => {
  let node;

  beforeEach(() => {
    node = document.createElement('div');
  });

  it('should return true if element does not have hidden class', () => {
    expect(isElementVisible(node)).to.eql(true);
  });

  it('should return false if element have hidden class', () => {
    node.classList.add('hidden');

    expect(isElementVisible(node)).to.eql(false);
  });
});
