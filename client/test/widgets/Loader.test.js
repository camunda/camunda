import {expect} from 'chai';
import {jsx} from 'view-utils';
import {mountTemplate} from 'testHelpers';
import {Loader} from 'widgets/Loader';

describe('<Loader>', () => {
  let node;

  describe('default loader', () => {
    beforeEach(() => {
      ({node} = mountTemplate(<Loader />));
    });

    it('should display spinner', () => {
      expect(node).to.contain('.spinner');
    });

    it('should contain loading text', () => {
      expect(node).to.contain.text('loading');
    });
  });

  describe('customized loader', () => {
    const text = 'text';
    const additionalClass = 'additional-class';

    beforeEach(() => {
      ({node} = mountTemplate(<Loader className={additionalClass} style="position: absolute">
        {text}
      </Loader>));
    });

    it('should display custom loading text', () => {
      expect(node).not.to.contain.text('loading');
      expect(node).to.contain.text(text);
    });

    it('should add custom class to loading_indicator element', () => {
      expect(node.querySelector('.loading_indicator')).to.have.class(additionalClass);
    });

    it('should use custom inline styles', () => {
      expect(node.querySelector('.loading_indicator').style.position)
        .to.eql('absolute');
    });
  });
});
