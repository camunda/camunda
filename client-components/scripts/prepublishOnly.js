import fs from 'node:fs';
import path from 'node:path';
import {execSync, spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

console.log('🧹 Cleaning monorepo...');

function runInParallel(scripts, cwd) {
	return Promise.all(
		scripts.map(
			(script) =>
				new Promise((resolve, reject) => {
					console.log(`🚀 Starting: npm run ${script}`);
					const child = spawn('npm', ['run', script], {
						cwd,
						stdio: 'inherit',
					});

					child.on('close', (code) => {
						if (code === 0) {
							console.log(`✅ Completed: npm run ${script}`);
							resolve();
						} else {
							reject(new Error(`npm run ${script} failed with code ${code}`));
						}
					});
				}),
		),
	);
}

function deleteDirectoriesRecursively(rootDir, targetDirName) {
	const items = fs.readdirSync(rootDir, {withFileTypes: true});

	for (const item of items) {
		const fullPath = path.join(rootDir, item.name);

		if (item.isDirectory()) {
			if (item.name === targetDirName) {
				console.log(`🗑️  Deleting: ${fullPath}`);
				fs.rmSync(fullPath, {recursive: true, force: true});
			} else {
				if (!item.name.startsWith('.')) {
					deleteDirectoriesRecursively(fullPath, targetDirName);
				}
			}
		}
	}
}

try {
	const __dirname = path.dirname(fileURLToPath(import.meta.url));
	const repoRoot = path.resolve(__dirname, '..');

	console.log('🗑️  Removing node_modules directories...');
	deleteDirectoriesRecursively(repoRoot, 'node_modules');

	console.log('🗑️  Removing dist directories...');
	deleteDirectoriesRecursively(repoRoot, 'dist');

	console.log('✅ Cleanup completed!');

	console.log('📦 Running npm ci...');
	execSync('npm ci --silent', {
		stdio: 'inherit',
		cwd: repoRoot,
	});

	console.log('🔧 Running lint, typecheck, and build in parallel...');
	await runInParallel(['lint', 'typecheck', 'build'], repoRoot);

	console.log('🎉 All done!');
} catch (error) {
	console.error('❌ Error:', error.message);
	process.exit(1);
}
