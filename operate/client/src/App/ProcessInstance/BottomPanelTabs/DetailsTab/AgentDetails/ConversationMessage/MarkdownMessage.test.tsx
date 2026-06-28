/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {MarkdownMessage} from './MarkdownMessage';

vi.unmock('react-markdown');

describe('<MarkdownMessage />', () => {
  it('should render plain text without crashing', () => {
    render(<MarkdownMessage content="Hello world" />);
    expect(screen.getByText('Hello world')).toBeInTheDocument();
  });

  it('should render empty string without crashing', () => {
    const {container} = render(<MarkdownMessage content="" />);
    expect(container).toBeInTheDocument();
  });

  it('should render inline code with InlineCode styling', () => {
    render(<MarkdownMessage content="Use `npm install` to install" />);
    const code = screen.getByText('npm install');
    expect(code.tagName).toBe('CODE');
  });

  it('should render fenced code blocks inside a pre element', () => {
    const content = '```js\nconst x = 1;\n```';
    render(<MarkdownMessage content={content} />);
    const code = screen.getByText('const x = 1;');
    expect(code.closest('pre')).toBeInTheDocument();
  });

  it('should render links with target="_blank" and rel="noopener noreferrer"', () => {
    render(<MarkdownMessage content="Visit [Camunda](https://camunda.io)" />);
    const link = screen.getByRole('link', {name: 'Camunda'});
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    expect(link).toHaveAttribute('href', 'https://camunda.io');
  });

  it('should not render disallowed elements like images', () => {
    render(<MarkdownMessage content="![alt](https://example.com/image.png)" />);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('should render strikethrough text with del element', () => {
    render(<MarkdownMessage content="This is ~~deleted~~ text" />);
    const del = screen.getByText('deleted');
    expect(del.tagName).toBe('DEL');
  });

  it('should shift heading levels down to keep the page outline intact', () => {
    render(<MarkdownMessage content={'# Title\n## Subtitle\n### Section'} />);
    expect(screen.getByText('Title').tagName).toBe('H3');
    expect(screen.getByText('Subtitle').tagName).toBe('H4');
    expect(screen.getByText('Section').tagName).toBe('H5');
  });

  it('should cap shifted heading levels at h6', () => {
    render(
      <MarkdownMessage content={'#### Deep\n##### Deeper\n###### Deepest'} />,
    );
    expect(screen.getByText('Deep').tagName).toBe('H6');
    expect(screen.getByText('Deeper').tagName).toBe('H6');
    expect(screen.getByText('Deepest').tagName).toBe('H6');
  });
});
