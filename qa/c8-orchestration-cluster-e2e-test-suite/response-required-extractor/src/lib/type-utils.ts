import { FieldSpec } from './types.js';

export function normalizeType(t: string): string {
  if (!t) return t;
  if (t.startsWith('string(')) return 'string';
  t = t.replace(/array<string\([^>]+\)>/g, 'array<string>');
  if (t === 'integer' || /^integer\(/.test(t)) t = 'number';
  t = t.replace(/\binteger(?:\([^)]*\))?\b/g, 'number');
  t = t.replace(/array<integer(?:\([^>]*\))?>/g, 'array<number>');
  // Collapse formatted numbers produced after integer replacement inside unions or generics
  t = t.replace(/\bnumber\([^)]*\)\b/g, 'number');
  t = t.replace(/array<number\([^>]*\)>/g, 'array<number>');
  return t;
}

export function refNameOf(t: string): string { return t.split('<').pop()!.replace(/>.*/, ''); }

export function dedupeFields(arr: FieldSpec[]): FieldSpec[] {
  const seen = new Set<string>();
  const out: FieldSpec[] = [];
  for (const f of arr) if (!seen.has(f.name)) { out.push(f); seen.add(f.name); }
  return out.sort((a,b)=>a.name.localeCompare(b.name));
}
