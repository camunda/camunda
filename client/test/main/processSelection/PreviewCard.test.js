import chai from 'chai';
import chaiEnzyme from 'chai-enzyme';
import sinon from 'sinon';
import {PreviewCardReact, __set__, __ResetDependency__} from 'main/processSelection/PreviewCard';
import React from 'react';
import {mount} from 'enzyme';
import {createReactMock} from 'testHelpers';

chai.use(chaiEnzyme());

const {expect} = chai;
const jsx = React.createElement;

describe('<PreviewCard>', () => {
  let DiagramPreview;
  let openDefinition;
  let setVersionForProcess;
  let node;

  beforeEach(() => {
    DiagramPreview = createReactMock('DiagramPreview');
    __set__('DiagramPreview', DiagramPreview);

    openDefinition = sinon.spy();
    __set__('openDefinition', openDefinition);

    setVersionForProcess = sinon.spy();
    __set__('setVersionForProcess', setVersionForProcess);
  });

  afterEach(() => {
    __ResetDependency__('DiagramPreview');
    __ResetDependency__('openDefinition');
    __ResetDependency__('setVersionForProcess');
  });

  describe('single version', () => {
    let current;
    let versions;
    let engineCount;

    beforeEach(() => {
      versions = [
        {
          id: 'processId',
          key: 'processKey',
          version: 1
        }
      ];

      current = {
        id: 'processId',
        key: 'processKey',
        name: 'processName',
        version: 1,
        engine: 'some engine',
        bpmn20Xml: 'some xml',
      };

      engineCount = 1;

      node = mount(<PreviewCardReact current={current} versions={versions} engineCount={engineCount} />);
    });

    it('should not display engine name when only one engine is available', () => {
      expect(node).not.to.contain.text(current.engine);
    });

    it('should display engine name when more than one engine is available', () => {
      node = mount(<PreviewCardReact current={current} versions={versions} engineCount={3} />);

      expect(node).to.contain.text(current.engine);
    });

    it('should display a preview diagram of the definition', () => {
      expect(node).to.contain.text('DiagramPreview');
    });

    it('should display the name of the process definition', () => {
      expect(node).to.contain.text('processName');
    });

    it('should display the version of the process definition', () => {
      expect(node).to.contain.text('1');
    });

    it('should not display a select element to choose a version', () => {
      expect(node).not.to.contain('select');
    });

    it('should open the definition when clicking on the diagram', () => {
      node.find('.diagram').simulate('click');

      expect(openDefinition.calledOnce).to.eql(true);
      expect(openDefinition.calledWith('processId')).to.eql(true);
    });

    it('should disable click listener if diagram is not defined', () => {
      current.bpmn20Xml = null;
      node = mount(<PreviewCardReact current={current} versions={versions} engineCount={1} />);

      node.find('.diagram').simulate('click');

      expect(openDefinition.calledOnce).to.eql(false);
    });

    it('should add no-xml class to diagram if bpmn20Xml is not defined', () => {
      current.bpmn20Xml = null;
      node = mount(<PreviewCardReact current={current} versions={versions} engineCount={1} />);

      expect(node.find('.diagram.no-xml')).to.present();
    });
  });

  describe('multiple versions', () => {
    let current;
    let versions;
    let engineCount;

    beforeEach(() => {
      versions = [
        {
          id: 'processId',
          key: 'processKey',
          version: 2
        },
        {
          id: 'processId1',
          version: 1,
          key: 'processKey'
        }
      ];

      current = {
        id: 'processId',
        key: 'processKey',
        name: 'processName',
        version: 1,
        engine: 'some engine',
        bpmn20Xml: 'some xml',
      };

      engineCount = 1;

      node = mount(<PreviewCardReact current={current} versions={versions} engineCount={engineCount} />);
    });

    it('should display a select to choose a version', () => {
      expect(node.find('select')).to.present();
    });

    it('should only display a single diagram', () => {
      expect(node.find('.diagram')).to.present();
    });

    it('should set the version when switching the version', () => {
      node.find('select').simulate('change', {
        target: {
          selectedIndex: 1
        }
      });

      expect(setVersionForProcess.calledOnce).to.eql(true);
      expect(setVersionForProcess.calledWith(current.id, versions[1])).to.eql(true);
    });

    it('should set the version from state', () => {
      expect(node.find('select')).to.have.value(current.version.toString());
    });

    it('should open the selected version', () => {
      node.find('.diagram').simulate('click');

      expect(openDefinition.calledOnce).to.eql(true);
      expect(openDefinition.calledWith('processId')).to.eql(true);
    });
  });
});
