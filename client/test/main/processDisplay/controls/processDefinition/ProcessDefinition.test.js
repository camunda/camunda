import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessDefinition, __set__, __ResetDependency__} from 'main/processDisplay/controls/processDefinition/ProcessDefinition';

describe('<ProcessDefinition>', () => {
  const initialState = {
    processDefinition: {
      availableProcessDefinitions: {
        state: 'INITIAL'
      }
    }
  };

  const loadedState = {
    processDefinition: {
      availableProcessDefinitions: {
        state: 'LOADED',
        data: [
          {
            id: 'id1',
            name: 'name1'
          },
          {
            id: 'id2',
            name: 'name2'
          }
        ]
      }
    }
  };

  let node;
  let update;
  let selectProcessDefinition;
  let loadProcessDefinitions;
  let onProcessDefinitionSelected;

  beforeEach(() => {
    loadProcessDefinitions = sinon.spy();
    __set__('loadProcessDefinitions', loadProcessDefinitions);

    selectProcessDefinition = sinon.spy();
    __set__('selectProcessDefinition', selectProcessDefinition);

    onProcessDefinitionSelected = sinon.spy();

    ({node, update} = mountTemplate(<ProcessDefinition
      selector="processDefinition"
      onProcessDefinitionSelected={onProcessDefinitionSelected}/>));
  });

  afterEach(() => {
    __ResetDependency__('loadProcessDefinitions');
    __ResetDependency__('selectProcessDefinition');
  });

  describe('initial state', () => {
    beforeEach(() => {
      update(initialState);
    });

    it('should load process definitions', () => {
      expect(loadProcessDefinitions.calledOnce).to.eql(true);
    });
  });

  describe('loaded state', () => {
    beforeEach(() => {
      update(loadedState);
    });

    it('should display a list of process definitions', () => {
      expect(node.textContent).to.include('name1');
      expect(node.textContent).to.include('name2');
    });

    describe('on selection', () => {
      let onNextUpdate;

      beforeEach(() => {
        onNextUpdate =  sinon.stub().callsArg(0);
        __set__('onNextUpdate', onNextUpdate);

        node.querySelector('select').value = 'id1';
        triggerEvent({
          node,
          selector: 'select',
          eventName: 'change'
        });
      });

      afterEach(() => {
        __ResetDependency__('onNextUpdate');
      });

      it('should select process definition', () => {
        expect(selectProcessDefinition.calledWith('id1')).to.eql(true);
      });

      it('should run onProcessDefinitionSelected in timeout', () => {
        expect(onNextUpdate.calledWith(onProcessDefinitionSelected));
        expect(onProcessDefinitionSelected.calledOnce).to.eql(true);
      });
    });
  });
});
