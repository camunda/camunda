import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import {ProcessInstanceCount} from 'main/processDisplay/ProcessInstanceCount';

describe('<ProcessInstanceCount>', () => {
  let node;
  let update;

  beforeEach(() => {
    ({node, update} = mountTemplate(<ProcessInstanceCount />));
  });

  it('should display the instance count', () => {
    update(123);

    expect(node.querySelector('.count')).to.exist;
    expect(node.querySelector('.count').textContent).to.eql('123');
  });

  it('should have a thousands separator', () => {
    update(12345);

    expect(node.querySelector('.count').textContent).to.eql('12.345');
  });
});
