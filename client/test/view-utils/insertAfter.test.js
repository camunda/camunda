import {expect} from 'chai';
import sinon from 'sinon';
import {insertAfter} from 'view-utils';

describe('insertAfter', () => {
  it('should insert element before next sibling of target', () => {
    const parentNode = {
      insertBefore: sinon.spy()
    };
    const target = {
      parentNode,
      nextSibling: 'nextSibling'
    };
    const node = 'inserted-node';

    insertAfter(node, target);

    expect(parentNode.insertBefore.calledWith(node, target.nextSibling)).to.eql(true);
  });
});
