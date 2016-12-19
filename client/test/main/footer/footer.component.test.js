import {expect} from 'chai';
import {mountTemplate} from 'testHelpers/mountTemplate';
import {jsx} from 'view-utils';
import {Footer} from 'main/footer/footer.component';

describe('<Footer>', () => {
  let node;
  let update;

  beforeEach(() => {
    ({node, update} = mountTemplate(<Footer/>));
  });

  it('should contain footer text', () => {
    expect(node).to.contain.text('Camunda Optimize Footer');
  });

  it('should have footer class', () => {
    expect(node.querySelector('footer')).to.have.class('footer');
  });

  it('should display current version', () => {
    const version = 'awesome-cat-version';

    update({version});

    expect(node).to.contain.text(version);
  })
});
