/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */

// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  tutorialSidebar: [
    'index',
    'collaboration-guidelines',
    {
      type: 'category',
      label: 'CI',
      link: { type: 'doc', id: 'ci' },
      items: ['ci-runbooks', 'flaky-test-gate'],
    },
    'infrastructure-services',
    'processes',
    'release',
    {
      type: 'category',
      label: 'CI',
      link: { type: 'doc', id: 'ci' },
      items: ['dependency-vulnerability-gate'],
    },
    'release',
  ],
};

module.exports = sidebars;
