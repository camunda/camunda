import {expect} from 'chai';
import {jsx, Text} from 'view-utils';
import {mountTemplate} from 'testHelpers';

describe('<Text>', () => {
  let node;
  let update;
  let state;

  beforeEach(() => {
    ({node, update} = mountTemplate(<Text property="prop" />));
    state = {
      prop: 'fff1'
    };
  });

  it('should insert empty text node into parent', () => {
    expect(node.innerText.trim()).to.eql('');
  });

  it('should set text from state after update', () => {
    update(state);

    expect(node).to.contain.text(state.prop);
  });
});
