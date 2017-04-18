import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {expect} from 'chai';
import {TargetValueInput} from 'main/processDisplay/diagram/targetValueDisplay/TargetValueInput';

describe('<TargetValueInput>', () => {
  it('should contain an input field', () => {
    const {node, update} = mountTemplate(<TargetValueInput unit="d" />);

    update();

    expect(node.querySelector('input')).to.exist;
  });

  it('should set the max attribute based on the passed unit', () => {
    const case1 = mountTemplate(<TargetValueInput unit="d" />);
    const case2 = mountTemplate(<TargetValueInput unit="s" />);

    case1.update();
    case2.update();

    expect(case1.node.querySelector('input').getAttribute('max')).to.eql('6');
    expect(case2.node.querySelector('input').getAttribute('max')).to.eql('59');
  });
});
