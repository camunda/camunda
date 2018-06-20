import * as utils from './utils';

const active = {state: utils.INSTANCE_STATE.ACTIVE, incidents: []};
const activeWithIncidents = {
  state: utils.INSTANCE_STATE.ACTIVE,
  incidents: [
    {
      id: '4295776400',
      errorType: 'IO_MAPPING_ERROR',
      errorMessage:
        'Could not apply output mappings: Task was completed without payload',
      state: utils.INSTANCE_STATE.ACTIVE,
      activityId: 'taskA'
    }
  ]
};
const completed = {state: utils.INSTANCE_STATE.COMPLETED, incidents: []};
const canceled = {state: utils.INSTANCE_STATE.CANCELED, incidents: []};

describe('utils.getActiveIncident', () => {
  it('should return an object if an instance has incidents', () => {
    expect(utils.getActiveIncident(activeWithIncidents)).toBe(null);
  });
  it('should return null if there is no incident', () => {
    expect(typeof utils.getActiveIncident(active)).toBe('object');
  });
});

describe('utils.getInstanceState', () => {
  it('should return the state for a completed instance', () => {
    const state = utils.getInstanceState({
      state: completed.state,
      incidents: completed.incidents
    });

    expect(state).toEqual(completed.state);
  });

  it('should return the state for a canceled instance', () => {
    const state = utils.getInstanceState({
      state: canceled.state,
      incidents: canceled.incidents
    });

    expect(state).toEqual(canceled.state);
  });

  it('should return the state for an active, incident free instance', () => {
    const state = utils.getInstanceState({
      state: active.state,
      incidents: active.incidents
    });

    expect(state).toEqual(active.state);
  });

  it('should return difrent state for an active instance with incidents', () => {
    const state = utils.getInstanceState({
      state: activeWithIncidents.state,
      incidents: activeWithIncidents.incidents
    });

    expect(state).not.toEqual(activeWithIncidents.state);
    expect(state).toEqual(utils.INSTANCE_STATE.INCIDENT);
  });
});

describe('utils.getIncidentMessage', () => {
  it('should return undefined for an instance with no incidents', () => {
    const message = utils.getIncidentMessage({incidents: active.incidents});

    expect(message).toEqual(undefined);
  });

  it('should return a string for an instance with incidents', () => {
    const message = utils.getIncidentMessage({
      incidents: activeWithIncidents.incidents
    });

    expect(message).toBe(activeWithIncidents.incidents[0].errorMessage);
  });
});
