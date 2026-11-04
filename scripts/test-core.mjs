import { readdirSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const root = fileURLToPath(new URL('..', import.meta.url));
const output = join(root, 'core/build/standalone-tests');
const sources = readdirSync(join(root, 'core/src'), { recursive: true })
  .filter(path => path.endsWith('.java')).map(path => join(root, 'core/src', path));
mkdirSync(output, { recursive: true });
for (const [command, args] of [
  ['javac', ['--release', '17', '-Werror', '-d', output, ...sources]],
  ['java', ['-cp', output, 'com.shubhraghav.lensqivo.core.CoreTests']],
]) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
