import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
export function run(cmd, args, opts = {}) {
    const res = spawnSync(cmd, args, { stdio: opts.inherit ? 'inherit' : 'pipe', encoding: 'utf8' });
    if (res.status !== 0)
        throw new Error(`${cmd} ${args.join(' ')} failed: ${res.stderr || res.status}`);
    return res.stdout;
}
export function sparseCheckout(repo, specPath, ref = 'main') {
    const workdir = join(tmpdir(), `spec-checkout-${Date.now()}`);
    run('git', ['init', workdir]);
    run('git', ['-C', workdir, 'remote', 'add', '-f', 'origin', repo]);
    run('git', ['-C', workdir, 'config', 'core.sparseCheckout', 'true']);
    writeFileSync(join(workdir, '.git', 'info', 'sparse-checkout'), specPath + '\n');
    run('git', ['-C', workdir, 'pull', 'origin', ref]);
    const commit = run('git', ['-C', workdir, 'rev-parse', 'HEAD']).trim();
    const specContent = readFileSync(join(workdir, specPath), 'utf8');
    return { workdir, commit, specContent };
}
//# sourceMappingURL=git.js.map