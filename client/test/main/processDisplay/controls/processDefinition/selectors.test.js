import {expect} from 'chai';
import {LOADING_STATE} from 'utils';
import {
  getDefinitionId,
  isLoadingProcessDefinitions,
  haveNoProcessDefinitions
} from 'main/processDisplay/controls/processDefinition/selectors';

describe('getDefinitionId', () => {
  it('should return selected process definition', () => {
    const selected = 'selected-id';

    expect(getDefinitionId({
      selected
    })).to.eql(selected);
  });
});

describe('isLoadingProcessDefinitions', () => {
  it('should return true when availableProcessDefinitions are being loaded', () => {
    expect(isLoadingProcessDefinitions({
      availableProcessDefinitions: {
        state: LOADING_STATE
      }
    })).to.eql(true);
  });

  it('should return false when availableProcessDefinitions are not being loaded', () => {
    expect(isLoadingProcessDefinitions({
      availableProcessDefinitions: {
        state: 'other'
      }
    })).to.eql(false);
  });
});

describe('haveNoProcessDefinitions', () => {
  it('should return true when availableProcessDefinitions have empty data', () => {
    expect(haveNoProcessDefinitions({
      availableProcessDefinitions: {
        data: []
      }
    })).to.eql(true);
  });

  it('should return true when availableProcessDefinitions have no property data', () => {
    expect(haveNoProcessDefinitions({
      availableProcessDefinitions: {}
    })).to.eql(true);
  });

  it('should return false when availableProcessDefinitions have process definitions', () => {
    expect(haveNoProcessDefinitions({
      availableProcessDefinitions: {
        data: ['id', 'id2']
      }
    })).to.eql(false);
  });
});
