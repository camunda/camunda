import {expect} from 'chai';
import {LOADING_STATE} from 'utils';
import {
  getDefinitionId,
  areControlsLoadingSomething,
  isDataEmpty
} from 'main/processDisplay/controls/selectors';

describe('getDefinitionId', () => {
  it('should return definition id from state', () => {
    const id = 'id-04';
    const state = {
      processDefinition: {
        selected: id
      }
    };

    expect(getDefinitionId(state)).to.eql(id);
  });
});

describe('areControlsLoadingSomething', () => {
  it('should return true when process definitions are being loaded', () => {
    const state = {
      processDefinition: {
        availableProcessDefinitions: {
          state: LOADING_STATE
        }
      }
    };

    expect(areControlsLoadingSomething(state)).to.eql(true);
  });

  it('should return false when process definitions are not being loaded', () => {
    const state = {
      processDefinition: {
        availableProcessDefinitions: {
          state: 'other'
        }
      }
    };

    expect(areControlsLoadingSomething(state)).to.eql(false);
  });
});

describe('isDataEmpty', () => {
  it('should return true when there no process definitions', () => {
    const state = {
      processDefinition: {
        availableProcessDefinitions: {
          data: []
        }
      }
    };

    expect(isDataEmpty(state)).to.eql(true);
  });

  it('should return false when there are process definitions', () => {
    const state = {
      processDefinition: {
        availableProcessDefinitions: {
          data: ['def']
        }
      }
    };

    expect(isDataEmpty(state)).to.eql(false);
  });
});
