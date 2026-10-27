/**
 * Codeowners Utility
 *
 * Handles resolution of file ownership using codeowners-cli
 */

import { execSync } from 'child_process';
import { existsSync } from 'fs';
import { join, dirname } from 'path';

// Cache for codeowners command info to avoid repeated detection
let cachedCommandInfo = null;

/**
 * Find the .codeowners file by traversing up the directory tree
 * @param {string} startDir - Directory to start searching from
 * @returns {string|null} Path to .codeowners file or null if not found
 */
function findCodeownersFile(startDir) {
  let currentDir = startDir;
  const root = '/';

  while (currentDir !== root) {
    // Check for .codeowners file (preferred)
    const codeownersPath = join(currentDir, '.codeowners');
    if (existsSync(codeownersPath)) {
      return currentDir;
    }

    // Check for CODEOWNERS file (fallback)
    const codeownersAltPath = join(currentDir, 'CODEOWNERS');
    if (existsSync(codeownersAltPath)) {
      return currentDir;
    }

    // Move up one directory
    const parentDir = dirname(currentDir);
    if (parentDir === currentDir) {
      // Reached root without finding .codeowners
      break;
    }
    currentDir = parentDir;
  }

  return null;
}

/**
 * Check if codeowners-cli is available
 * Tries multiple detection methods:
 * 1. Direct PATH lookup (global install)
 * 2. User's shell environment (for aliases)
 * 3. Local node_modules/.bin directory
 * 4. npx lookup (local install)
 * @returns {boolean} True if codeowners-cli is available
 */
export function isCodeownersCliAvailable() {
  // Method 1: Try direct command first (global install or in PATH)
  try {
    execSync('codeowners-cli --version', { stdio: ['ignore', 'pipe', 'ignore'] });
    return true;
  } catch (e) {
    // Ignore and try next method
  }

  // Method 2: Try with user's shell (for aliases in zsh/bash)
  const userShell = process.env.SHELL || '/bin/bash';
  try {
    execSync(`${userShell} -i -c "codeowners-cli --version"`, {
      stdio: ['ignore', 'pipe', 'ignore'],
      timeout: 5000 // 5 second timeout to avoid hanging
    });
    return true;
  } catch (e) {
    // Ignore and try next method
  }

  // Method 3: Try local node_modules/.bin
  try {
    execSync('./node_modules/.bin/codeowners-cli --version', {
      stdio: ['ignore', 'pipe', 'ignore']
    });
    return true;
  } catch (e) {
    // Ignore and try next method
  }

  // Method 4: Try npx (local install)
  try {
    execSync('npx --no-install codeowners-cli --version', {
      stdio: ['ignore', 'pipe', 'ignore']
    });
    return true;
  } catch (npxError) {
    // All methods failed
    return false;
  }
}

/**
 * Determine the appropriate command to run codeowners-cli
 * Uses the same detection logic as isCodeownersCliAvailable
 * Results are cached to avoid repeated detection
 * @returns {{command: string, useShell: boolean}} Command info
 */
function getCodeownersCommand() {
  // Return cached result if available
  if (cachedCommandInfo !== null) {
    return cachedCommandInfo;
  }

  // Method 1: Try direct command
  try {
    execSync('codeowners-cli --version', { stdio: ['ignore', 'pipe', 'ignore'] });
    cachedCommandInfo = { command: 'codeowners-cli', useShell: false };
    return cachedCommandInfo;
  } catch (e) {
    // Ignore and try next method
  }

  // Method 2: Try with user's shell (for aliases)
  const userShell = process.env.SHELL || '/bin/bash';
  try {
    execSync(`${userShell} -i -c "codeowners-cli --version"`, {
      stdio: ['ignore', 'pipe', 'ignore'],
      timeout: 5000
    });
    cachedCommandInfo = { command: 'codeowners-cli', useShell: true, shell: userShell };
    return cachedCommandInfo;
  } catch (e) {
    // Ignore and try next method
  }

  // Method 3: Try local node_modules/.bin
  try {
    execSync('./node_modules/.bin/codeowners-cli --version', {
      stdio: ['ignore', 'pipe', 'ignore']
    });
    cachedCommandInfo = { command: './node_modules/.bin/codeowners-cli', useShell: false };
    return cachedCommandInfo;
  } catch (e) {
    // Ignore and try next method
  }

  // Method 4: Fall back to npx
  cachedCommandInfo = { command: 'npx --no-install codeowners-cli', useShell: false };
  return cachedCommandInfo;
}

/**
 * Get codeowners for a file using codeowners-cli
 * @param {string} relativePath - Relative path from repository root
 * @param {string} repoRoot - Repository root directory (will traverse up to find .codeowners)
 * @returns {Array<string>} Array of owner teams or ['(unowned)']
 * @throws {Error} Only if no .codeowners file is found in the directory tree
 */
export function getCodeowners(relativePath, repoRoot) {
  // Find the actual repository root with .codeowners file by traversing up
  const actualRepoRoot = findCodeownersFile(repoRoot);

  if (!actualRepoRoot) {
    throw new Error(
      `No .codeowners or CODEOWNERS file found in directory tree starting from ${repoRoot}. ` +
      'Please ensure you are running the script from within a git repository with a .codeowners file at the root.'
    );
  }

  // If codeowners-cli is not available, return unowned
  // The collector will check for this at startup and give a proper error message
  if (!isCodeownersCliAvailable()) {
    return ['(unowned)'];
  }

  try {
    const cmdInfo = getCodeownersCommand();
    let fullCommand = `${cmdInfo.command} owner "${relativePath}"`;

    const execOptions = {
      cwd: actualRepoRoot,
      encoding: 'utf-8',
      stdio: ['pipe', 'pipe', 'pipe']
    };

    // If using shell with alias, wrap the command with shell -i -c
    if (cmdInfo.useShell) {
      const escapedCommand = fullCommand.replace(/"/g, '\\"');
      fullCommand = `${cmdInfo.shell} -i -c "${escapedCommand}"`;
    }

    const result = execSync(fullCommand, execOptions);

    // Parse the output (format: "@team1 @team2")
    const owners = result.trim().split(/\s+/).filter(o => o.startsWith('@'));
    return owners.length > 0 ? owners : ['(unowned)'];
  } catch (error) {
    // If codeowners-cli fails (e.g., file not in .codeowners), return unowned
    return ['(unowned)'];
  }
}

/**
 * Get codeowners for multiple files in a single batch operation
 * Much faster than calling getCodeowners() for each file individually
 * @param {Array<string>} relativePaths - Array of relative paths from repository root
 * @param {string} repoRoot - Repository root directory (will traverse up to find .codeowners)
 * @param {boolean} verbose - Whether to output progress (default: false)
 * @returns {Map<string, Array<string>>} Map of file path to array of owner teams
 * @throws {Error} Only if no .codeowners file is found in the directory tree
 */
export function getBatchCodeowners(relativePaths, repoRoot, verbose = false) {
  // Find the actual repository root with .codeowners file by traversing up
  const actualRepoRoot = findCodeownersFile(repoRoot);

  if (!actualRepoRoot) {
    throw new Error(
      `No .codeowners or CODEOWNERS file found in directory tree starting from ${repoRoot}. ` +
      'Please ensure you are running the script from within a git repository with a .codeowners file at the root.'
    );
  }

  // If codeowners-cli is not available, return unowned for all files
  if (!isCodeownersCliAvailable()) {
    const result = new Map();
    for (const path of relativePaths) {
      result.set(path, ['(unowned)']);
    }
    return result;
  }

  // Split into chunks to avoid command line length limits
  // Typical path length is ~80 chars, ARG_MAX is 2MB, so 500 files per chunk is safe
  const CHUNK_SIZE = 500;
  const allOwnersMap = new Map();

  // Debug: Log command detection once
  const cmdInfo = getCodeownersCommand();
  const debugEnabled = process.env.DEBUG_CODEOWNERS === 'true';
  if (debugEnabled) {
    console.error('[DEBUG] Codeowners command info:', JSON.stringify(cmdInfo));
    console.error('[DEBUG] Processing', relativePaths.length, 'files in', Math.ceil(relativePaths.length / CHUNK_SIZE), 'chunks');
  }

  const totalChunks = Math.ceil(relativePaths.length / CHUNK_SIZE);

  for (let i = 0; i < relativePaths.length; i += CHUNK_SIZE) {
    const chunk = relativePaths.slice(i, i + CHUNK_SIZE);
    const chunkNum = Math.floor(i / CHUNK_SIZE) + 1;

    // Show progress for verbose mode
    if (verbose && totalChunks > 1) {
      const processed = Math.min(i + CHUNK_SIZE, relativePaths.length);
      const percent = Math.round((processed / relativePaths.length) * 100);
      console.error(`   Progress: ${processed}/${relativePaths.length} (${percent}%) - chunk ${chunkNum}/${totalChunks}`);
    }

    try {
      // Use the 'owner' command with multiple files
      // codeowners-cli supports passing multiple files as arguments
      // Build command with all paths - need to escape paths properly
      const escapedPaths = chunk.map(p => `"${p}"`).join(' ');
      let fullCommand = `${cmdInfo.command} owner ${escapedPaths}`;

      if (debugEnabled) {
        console.error(`[DEBUG] Chunk ${chunkNum}: Processing ${chunk.length} files`);
        console.error(`[DEBUG] First file: ${chunk[0]}`);
        console.error(`[DEBUG] Command length: ${fullCommand.length} chars`);
        console.error(`[DEBUG] useShell: ${cmdInfo.useShell}, shell: ${cmdInfo.shell || 'default'}`);
      }

      const execOptions = {
        cwd: actualRepoRoot,
        encoding: 'utf-8',
        stdio: ['pipe', 'pipe', 'pipe'],
        maxBuffer: 100 * 1024 * 1024 // 100MB buffer for large outputs
      };

      // If using shell with alias, wrap the command with shell -i -c
      if (cmdInfo.useShell) {
        // Need to escape the command for shell -i -c execution
        // Double quotes need to be escaped, and the whole command needs to be quoted
        const escapedCommand = fullCommand.replace(/"/g, '\\"');
        fullCommand = `${cmdInfo.shell} -i -c "${escapedCommand}"`;

        if (debugEnabled) {
          console.error(`[DEBUG] Wrapped command for alias: ${fullCommand.substring(0, 200)}...`);
        }
      }

      const result = execSync(fullCommand, execOptions);

      if (debugEnabled) {
        console.error(`[DEBUG] Chunk ${chunkNum}: Got ${result.length} bytes of output`);
        console.error(`[DEBUG] First 200 chars: ${result.substring(0, 200)}`);
      }

      // Parse the output - codeowners-cli outputs in a specific format when multiple files are passed:
      // Format:
      //   <path>:
      //   @team1 @team2
      //
      //   <path2>:
      //   @team3
      // Note: There's a newline after the colon, then owners on the next line
      const lines = result.trim().split('\n');

      let currentPath = null;
      for (const line of lines) {
        const trimmedLine = line.trim();

        // Empty lines separate entries
        if (trimmedLine === '') {
          currentPath = null;
          continue;
        }

        // Lines ending with : are file paths
        if (trimmedLine.endsWith(':')) {
          currentPath = trimmedLine.substring(0, trimmedLine.length - 1).trim();
          // Initialize with empty array, will be filled on next line(s)
          allOwnersMap.set(currentPath, []);
        } else if (currentPath !== null) {
          // This line contains owners for the current path
          const owners = trimmedLine.split(/\s+/).filter(o => o.startsWith('@'));
          if (owners.length > 0) {
            allOwnersMap.set(currentPath, owners);
          } else {
            allOwnersMap.set(currentPath, ['(unowned)']);
          }
          currentPath = null; // Reset after processing owners
        }
      }
    } catch (error) {
      // If this chunk fails, mark all files in the chunk as unowned
      if (debugEnabled) {
        console.error(`[DEBUG] Chunk ${chunkNum}: ERROR - ${error.message}`);
        if (error.stderr) {
          console.error(`[DEBUG] Stderr: ${error.stderr.toString().substring(0, 500)}`);
        }
        if (error.stdout) {
          console.error(`[DEBUG] Stdout: ${error.stdout.toString().substring(0, 500)}`);
        }
      }
      for (const path of chunk) {
        if (!allOwnersMap.has(path)) {
          allOwnersMap.set(path, ['(unowned)']);
        }
      }
    }
  }

  // Ensure all requested paths have a result (fill in missing with unowned)
  for (const path of relativePaths) {
    if (!allOwnersMap.has(path)) {
      allOwnersMap.set(path, ['(unowned)']);
    } else if (allOwnersMap.get(path).length === 0) {
      // Path was found but no owners were set (empty line after colon)
      allOwnersMap.set(path, ['(unowned)']);
    }
  }

  return allOwnersMap;
}
