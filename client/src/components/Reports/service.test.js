import {update} from './service';

describe('update', () => {
  it('should reset the process parts changing the view to something not process instance duration like', () => {
    const spy = jest.fn();

    update({
      type: 'view',
      data: {operation: 'rawData'},
      view: {operation: 'avg', property: 'duration', entity: 'processInstance'},
      groupBy: {type: 'none'},
      visualization: 'number',
      callback: spy
    });

    expect(spy).toHaveBeenCalled();
    expect(spy.mock.calls[0][0].parameters).toEqual({processPart: {$set: null}});
  });
});
