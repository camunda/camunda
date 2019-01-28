import {update} from './service';

const countProcessInstances = {
  entity: 'processInstance',
  operation: 'count',
  property: 'frequency'
};

const processInstanceDuration = {
  entity: 'processInstance',
  operation: 'avg',
  property: 'duration'
};

const startDate = {
  type: 'startDate',
  value: {unit: 'month'}
};

describe('update', () => {
  it('should just update visualization', () => {
    const spy = jest.fn();

    update('visualization', 'bar', {updateReport: spy});

    expect(spy).toHaveBeenCalledWith({visualization: {$set: 'bar'}});
  });

  it('should update groupby', () => {
    const spy = jest.fn();

    update('groupBy', startDate, {
      updateReport: spy,
      view: countProcessInstances,
      visualization: 'bar'
    });

    expect(spy).toHaveBeenCalledWith({groupBy: {$set: startDate}}, true);
  });

  it("should reset visualization when it's incompatible with the new group", () => {
    const spy = jest.fn();

    update('groupBy', startDate, {
      updateReport: spy,
      view: countProcessInstances,
      visualization: 'number'
    });

    expect(spy).toHaveBeenCalledWith(
      {groupBy: {$set: startDate}, visualization: {$set: null}},
      true
    );
  });

  it('should automatically select an unambiguous visualization when updating group', () => {
    const spy = jest.fn();

    update(
      'groupBy',
      {type: 'none'},
      {
        updateReport: spy,
        view: countProcessInstances,
        visualization: 'heat'
      }
    );

    expect(spy).toHaveBeenCalledWith(
      {groupBy: {$set: {type: 'none'}}, visualization: {$set: 'number'}},
      true
    );
  });

  it('should update view', () => {
    const spy = jest.fn();

    update('view', countProcessInstances, {
      updateReport: spy,
      groupBy: startDate,
      visualization: 'bar'
    });

    expect(spy).toHaveBeenCalledWith(
      {view: {$set: countProcessInstances}, parameters: {processPart: {$set: null}}},
      true
    );
  });

  it('should adjust groupby and visualization when changing view', () => {
    const spy = jest.fn();

    update('view', countProcessInstances, {
      updateReport: spy,
      groupBy: {type: 'flowNodes'},
      visualization: 'heat'
    });

    expect(spy).toHaveBeenCalledWith(
      {
        view: {$set: countProcessInstances},
        groupBy: {$set: null},
        visualization: {$set: null},
        parameters: {processPart: {$set: null}}
      },
      true
    );
  });

  it('should reset the process parts changing the view to something not process instance duration like', () => {
    const spy = jest.fn();

    update(
      'view',
      {operation: 'rawData'},
      {
        groupBy: {type: 'none'},
        visualization: 'number',
        updateReport: spy
      }
    );

    expect(spy).toHaveBeenCalled();
    expect(spy.mock.calls[0][0].parameters).toEqual({processPart: {$set: null}});
  });

  it('should not reset the process parts when changing within a process instance duration view', () => {
    const spy = jest.fn();

    update('view', processInstanceDuration, {
      groupBy: {type: 'none'},
      visualization: 'number',
      updateReport: spy
    });

    expect(spy).toHaveBeenCalledWith({view: {$set: processInstanceDuration}}, true);
  });
});
