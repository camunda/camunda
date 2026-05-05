#!/usr/bin/env node
/**
 * Build script for camunda-process-test-coverage-frontend-new.
 *
 * This script copies the required static resources (bpmn-js, Bootstrap, Bootstrap Icons, and
 * the application source files) into the target directory used by the Maven build.
 *
 * The output directory is:
 *   ../camunda-process-test-java/target/generated-frontend-resources/coverage/
 *
 * Output structure:
 *   coverage/
 *   ├── index.html              (HTML template with {{ COVERAGE_DATA }} placeholder)
 *   └── static/
 *       ├── vendor/
 *       │   ├── bpmn-js/        (bpmn-js viewer bundle and CSS)
 *       │   ├── bootstrap/      (Bootstrap CSS and JS)
 *       │   └── bootstrap-icons/(Bootstrap Icons CSS and fonts)
 *       ├── media/              (logo and favicon)
 *       ├── app.js              (application JavaScript)
 *       └── styles.css          (custom styles)
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const NODE_MODULES = path.join(ROOT, 'node_modules');

const BUILD_PATH =
  process.env.BUILD_PATH ||
  path.resolve(
    ROOT,
    '../camunda-process-test-java/target/generated-frontend-resources/coverage'
  );

const STATIC = path.join(BUILD_PATH, 'static');

/**
 * Creates a directory (and parents) if it does not exist.
 * @param {string} dir
 */
function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

/**
 * Copies a single file from src to dest.
 * @param {string} src
 * @param {string} dest
 */
function copyFile(src, dest) {
  ensureDir(path.dirname(dest));
  fs.copyFileSync(src, dest);
}

/**
 * Recursively copies a directory from src to dest.
 * @param {string} src
 * @param {string} dest
 */
function copyDir(src, dest) {
  ensureDir(dest);
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

console.log(`Building coverage frontend to: ${BUILD_PATH}`);

// ── bpmn-js ───────────────────────────────────────────────────────────────────
const BPMN_JS_SRC = path.join(NODE_MODULES, 'bpmn-js', 'dist');
const BPMN_JS_DEST = path.join(STATIC, 'vendor', 'bpmn-js');

ensureDir(BPMN_JS_DEST);
copyFile(
  path.join(BPMN_JS_SRC, 'bpmn-navigated-viewer.production.min.js'),
  path.join(BPMN_JS_DEST, 'bpmn-navigated-viewer.production.min.js')
);
// Copy the complete assets directory (preserves relative font paths inside CSS)
copyDir(path.join(BPMN_JS_SRC, 'assets'), path.join(BPMN_JS_DEST, 'assets'));
console.log('  ✓ bpmn-js');

// ── Bootstrap ─────────────────────────────────────────────────────────────────
const BS_DEST = path.join(STATIC, 'vendor', 'bootstrap');
ensureDir(BS_DEST);
copyFile(
  path.join(NODE_MODULES, 'bootstrap', 'dist', 'css', 'bootstrap.min.css'),
  path.join(BS_DEST, 'bootstrap.min.css')
);
copyFile(
  path.join(NODE_MODULES, 'bootstrap', 'dist', 'js', 'bootstrap.bundle.min.js'),
  path.join(BS_DEST, 'bootstrap.bundle.min.js')
);
console.log('  ✓ bootstrap');

// ── Bootstrap Icons ───────────────────────────────────────────────────────────
const BI_DEST = path.join(STATIC, 'vendor', 'bootstrap-icons');
ensureDir(BI_DEST);
copyFile(
  path.join(NODE_MODULES, 'bootstrap-icons', 'font', 'bootstrap-icons.min.css'),
  path.join(BI_DEST, 'bootstrap-icons.min.css')
);
copyDir(
  path.join(NODE_MODULES, 'bootstrap-icons', 'font', 'fonts'),
  path.join(BI_DEST, 'fonts')
);
console.log('  ✓ bootstrap-icons');

// ── Application sources ───────────────────────────────────────────────────────
const SRC = path.join(ROOT, 'src');

copyFile(path.join(SRC, 'index.html'), path.join(BUILD_PATH, 'index.html'));
copyFile(path.join(SRC, 'app.js'), path.join(STATIC, 'app.js'));
copyFile(path.join(SRC, 'styles.css'), path.join(STATIC, 'styles.css'));
console.log('  ✓ application sources');

// ── Static media ──────────────────────────────────────────────────────────────
const MEDIA_SRC = path.join(ROOT, 'public', 'static', 'media');
const MEDIA_DEST = path.join(STATIC, 'media');

if (fs.existsSync(MEDIA_SRC)) {
  copyDir(MEDIA_SRC, MEDIA_DEST);
  console.log('  ✓ static media');
}

console.log('Build complete.');
