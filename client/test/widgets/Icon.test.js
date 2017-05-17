import {jsx} from 'view-utils';
import {expect} from 'chai';
import {mountTemplate} from 'testHelpers';
import {Icon} from 'widgets/Icon';

describe('<Icon>', () => {
  let node;

  beforeEach(() => {
    ({node} = mountTemplate(<Icon icon="ok" />));
  });

  it('should render icon with expected classes', () => {
    const iconSpan = node.querySelector('span');

    expect(iconSpan).to.have.class('glyphicon');
    expect(iconSpan).to.have.class('glyphicon-ok');
  });
});
