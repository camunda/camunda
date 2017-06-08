import {expect} from 'chai';
import {jsx, decorateWithSelector} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';

describe('decorateWithSelector', () => {
  let Child;
  let Component;
  let update;
  let node;

  beforeEach(() => {
    Child = createMockComponent('Child');
    Component = decorateWithSelector(Child, 'views');

    ({node, update} = mountTemplate(<Component selector="something-else" />));
  });

  it('should use selector to change scope applied to client component', () => {
    update({views: 123});

    expect(Child.updatedWith(123)).to.eql(true);
  });

  it('should render client component', () => {
    expect(node).to.contain.text(Child.text);
  });

  it('should pass attributes to client component', () => {
    expect(Child.appliedWith({selector: 'something-else'})).to.eql(true);
  });
});
