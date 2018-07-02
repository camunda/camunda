export const FILTER_TYPES = {
  RUNNING: 'running',
  FINISHED: 'finished'
};

export const FILTER_SELECTION = {
  running: {
    active: true,
    incidents: true
  },
  incidents: {
    active: false,
    incidents: true
  },
  active: {
    active: true,
    incidents: false
  }
};

export const DEFAULT_FILTER = FILTER_SELECTION.running;
export const INCIDENTS_FILTER = FILTER_SELECTION.incidents;
