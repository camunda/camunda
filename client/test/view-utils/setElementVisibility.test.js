import chaiDom from 'chai-dom';
import chai from 'chai';
import {setElementVisibility} from 'view-utils';

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
