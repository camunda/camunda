import {expect} from 'chai';
import {reducer, createSelectProcessDefinitionAction,
        createLoadProcessDefinitionsAction, createLoadProcessDefinitionsResultAction} from 'main/processDisplay/controls/processDefinition/processDefinition.reducer';
import {INITIAL_STATE, LOADING_STATE, LOADED_STATE} from 'utils/loading';

describe('ProcessDefinition reducer', () => {
  const availableProcesses = [
    {id: 'id1', name: 'name1'},
    {id: 'id2', name: 'name2'}
  ];

  let availableProcessDefinitions,
      selected;

  it('should set selected property on select process definition action', () => {
    ({selected} = reducer(undefined, createSelectProcessDefinitionAction('someId')));

    expect(selected).to.eql('someId');
  });

  describe('availableProcessDefinitions', () => {
    describe('initial state', () => {
      it('contains availableProcessDefinitions', () => {
        ({availableProcessDefinitions} = reducer(undefined, {type: '@@INIT'}));

        expect(typeof availableProcessDefinitions).to.eql('object');
        expect(availableProcessDefinitions.state).to.eql(INITIAL_STATE);
      });
    });

    describe('loading state', () => {
      it('sets state to loading', () => {
        ({availableProcessDefinitions} = reducer(undefined, createLoadProcessDefinitionsAction()));

        expect(availableProcessDefinitions.state).to.eql(LOADING_STATE);
      });
    });

    describe('loaded state', () => {
      beforeEach(() => {
        ({availableProcessDefinitions} = reducer(undefined, createLoadProcessDefinitionsResultAction(availableProcesses)));
      });

      it('sets state to loaded', () => {
        expect(availableProcessDefinitions.state).to.eql(LOADED_STATE);
      });

      it('contains the data', () => {
        expect(availableProcessDefinitions.data).to.eql(availableProcesses);
      });
    });
  });
});
