import {expect} from 'chai';
import {reducer, createDeleteFilterAction} from 'main/processDisplay/controls/filter/routeReducer';

describe('Filter route reducer', () => {
  it('should delete a filter', () => {
    const filter = {
      type: 'someType',
      data: {
        some: 'very',
        important: 'data'
      }
    };
    const state = reducer([filter], createDeleteFilterAction({
      some: 'very',
      important: 'data'
    }));

    expect(state.length).to.eql(0);
  });
});
