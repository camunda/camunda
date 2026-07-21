import { appendFileSync } from 'node:fs';

/**
 * ponytail: the ~7 GitHub Actions toolkit calls we actually use, inlined.
 * @actions/core drags in @actions/exec + http-client + io (~400kB) for OIDC and
 * command features this action never touches. These are the documented Actions
 * command/file protocols — nothing clever.
 */

const escape = (s: string): string => s.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');

const appendEnvFile = (envVar: string, content: string): void => {
  const file = process.env[envVar];
  if (file) appendFileSync(file, content);
};

export const getInput = (name: string, opts: { required?: boolean } = {}): string => {
  const value = (process.env[`INPUT_${name.toUpperCase().replace(/ /g, '_')}`] ?? '').trim();
  if (opts.required && !value) throw new Error(`Input required and not supplied: ${name}`);
  return value;
};

export const getBooleanInput = (name: string): boolean => getInput(name).toLowerCase() === 'true';

// GITHUB_OUTPUT file protocol with a heredoc delimiter (safe for multiline values).
export const setOutput = (name: string, value: string): void =>
  appendEnvFile('GITHUB_OUTPUT', `${name}<<_GHA_EOF_\n${value}\n_GHA_EOF_\n`);

export const info = (msg: string): void => {
  process.stdout.write(`${msg}\n`);
};
export const warning = (msg: string): void => {
  process.stdout.write(`::warning::${escape(msg)}\n`);
};
export const setFailed = (msg: string): void => {
  process.stdout.write(`::error::${escape(msg)}\n`);
  process.exitCode = 1;
};

class Summary {
  private buf = '';
  addHeading(text: string, level = 1): this {
    this.buf += `<h${level}>${text}</h${level}>\n`;
    return this;
  }
  addList(items: string[]): this {
    this.buf += `<ul>${items.map((i) => `<li>${i}</li>`).join('')}</ul>\n`;
    return this;
  }
  async write(): Promise<void> {
    appendEnvFile('GITHUB_STEP_SUMMARY', this.buf);
    this.buf = '';
  }
}

export const summary = new Summary();
