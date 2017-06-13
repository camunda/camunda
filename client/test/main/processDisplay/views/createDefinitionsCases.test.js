import {jsx, Match} from 'view-utils';
import {expect} from 'chai';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {createDefinitionCases, __set__, __ResetDependency__} from 'main/processDisplay/views/createDefinitionCases';

describe('main/processDisplay/views createDefinitionCases', () => {
  let node;
  let update;
  let currentView;
  let shouldDisplay;
  let definitions;

  beforeEach(() => {
    currentView = 'd1';
    shouldDisplay = view => view === currentView;

    definitions = {
      d1: {
        Component: createMockComponent('d1')
      },
      d2: {
        Component: createMockComponent('d2')
      }
    };

    __set__('definitions', definitions);

    ({node, update} = mountTemplate(<Match>
      {
        createDefinitionCases('Component', shouldDisplay)
      }
    </Match>));
  });

  afterEach(() => {
    __ResetDependency__('definitions');
  });

  it('should display d1 Component when d1 view is choosen', () => {
    update();

    expect(node).to.contain.text(definitions.d1.Component.text);
  });

  it('should display d2 Component when d2 view is choosen', () => {
    currentView = 'd2';
    update();

    expect(node).to.contain.text(definitions.d2.Component.text);
  });
});
