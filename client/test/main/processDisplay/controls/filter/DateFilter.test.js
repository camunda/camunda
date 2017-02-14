import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import {DateFilter} from 'main/processDisplay/controls/filter/DateFilter';

describe('<DateFilter>', () => {
  let node;
  let update;
  let state;

  const start = '2016-12-01T00:00:00';
  const end = '2016-12-31T23:59:59';

  beforeEach(() => {
    state = {filter: {
      start,
      end
    }};

    ({node, update} = mountTemplate(<DateFilter selector="filter"/>));

    update(state);
  });

  it('contain the formatted start date', () => {
    expect(node.textContent).to.contain('2016-12-01');
  });

  it('should contain the formatted end date', () => {
    expect(node.textContent).to.contain('2016-12-31');
  });

  it('should strip any time information', () => {
    expect(node.textContent).to.not.contain('00:00:00');
    expect(node.textContent).to.not.contain('23:59:59');
  });
});
