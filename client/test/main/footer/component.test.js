import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import {Footer} from 'main/footer/component';

describe('<Footer>', () => {
  let node;
  let update;

  beforeEach(() => {
    ({node, update} = mountTemplate(<Footer/>));
  });

  it('should contain Copyright text', () => {
    expect(node).to.contain.text('Camunda services GmbH');
  });

  it('should display current version', () => {
    const version = 'awesome-test-version';

    update({version});

    expect(node).to.contain.text(version);
  });
});
