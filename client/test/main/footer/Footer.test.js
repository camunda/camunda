import {expect} from 'chai';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {jsx} from 'view-utils';
import {Footer, __set__, __ResetDependency__} from 'main/footer/Footer';

describe('<Footer>', () => {
  let node;
  let update;
  let Progress;

  beforeEach(() => {
    Progress = createMockComponent('Progress');
    __set__('Progress', Progress);

    ({node, update} = mountTemplate(<Footer/>));
  });

  afterEach(() => {
    __ResetDependency__('Progress');
  });

  it('should contain Copyright text', () => {
    expect(node).to.contain.text('Camunda services GmbH');
  });

  it('should display current version', () => {
    const version = 'awesome-test-version';

    update({version});

    expect(node).to.contain.text(version);
  });

  it('should display import progress', () => {
    expect(node).to.contain.text(Progress.text);
  });
});
