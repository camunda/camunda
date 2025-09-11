export function normalizeType(t) {
    if (!t)
        return t;
    if (t.startsWith('string('))
        return 'string';
    t = t.replace(/array<string\([^>]+\)>/g, 'array<string>');
    if (t === 'integer' || /^integer\(/.test(t))
        t = 'number';
    t = t.replace(/\binteger(?:\([^)]*\))?\b/g, 'number');
    t = t.replace(/array<integer(?:\([^>]*\))?>/g, 'array<number>');
    // Collapse formatted numbers produced after integer replacement inside unions or generics
    t = t.replace(/\bnumber\([^)]*\)\b/g, 'number');
    t = t.replace(/array<number\([^>]*\)>/g, 'array<number>');
    return t;
}
export function refNameOf(t) { return t.split('<').pop().replace(/>.*/, ''); }
export function dedupeFields(arr) {
    const seen = new Set();
    const out = [];
    for (const f of arr)
        if (!seen.has(f.name)) {
            out.push(f);
            seen.add(f.name);
        }
    return out.sort((a, b) => a.name.localeCompare(b.name));
}
//# sourceMappingURL=type-utils.js.map