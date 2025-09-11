import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import yaml from 'js-yaml';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
const REPO = 'https://github.com/camunda/camunda-orchestration-cluster-api.git';
const SPEC_PATH = 'specification/rest-api.yaml';
function sparseCheckoutSpec() {
    const workdir = join(tmpdir(), `spec-checkout-${Date.now()}`);
    // init repo
    run('git', ['init', workdir]);
    runIn(workdir, 'git', ['remote', 'add', '-f', 'origin', REPO]);
    runIn(workdir, 'git', ['config', 'core.sparseCheckout', 'true']);
    writeFileSync(join(workdir, '.git', 'info', 'sparse-checkout'), SPEC_PATH + '\n');
    runIn(workdir, 'git', ['pull', 'origin', 'main']);
    const commit = runIn(workdir, 'git', ['rev-parse', 'HEAD']).trim();
    const specContent = readFileSync(join(workdir, SPEC_PATH), 'utf8');
    return { workdir, commit, specContent };
}
function run(cmd, args) {
    const res = spawnSync(cmd, args, { stdio: 'inherit' });
    if (res.status !== 0)
        throw new Error(`${cmd} ${args.join(' ')} failed`);
}
function runIn(cwd, cmd, args) {
    const res = spawnSync(cmd, args, { cwd, encoding: 'utf8' });
    if (res.status !== 0)
        throw new Error(`${cmd} ${args.join(' ')} failed`);
    return res.stdout;
}
function extractResponses(doc) {
    const entries = [];
    const paths = doc.paths || {};
    for (const [path, pathItem] of Object.entries(paths)) {
        for (const method of ['get', 'post', 'put', 'patch', 'delete']) {
            const op = pathItem[method];
            if (!op)
                continue;
            const responses = op.responses || {};
            for (const [status, response] of Object.entries(responses)) {
                const content = response?.content;
                const appJson = content?.['application/json'];
                const schema = appJson?.schema;
                if (!schema)
                    continue;
                const flattened = buildSchemaTree(schema, doc.components?.schemas || {});
                entries.push({
                    path,
                    method: method.toUpperCase(),
                    status,
                    schema: {
                        required: flattened.required,
                        optional: flattened.optional,
                    },
                });
            }
        }
    }
    return entries;
}
function buildSchemaTree(schema, components, seen = new Set()) {
    const flat = flatten(schema, components, seen);
    // For each field that is object/array<object> or $ref to object, build children
    for (const group of [flat.required, flat.optional]) {
        for (const field of group) {
            const resolved = resolveFieldSchema(field.name, schema, components);
            if (resolved) {
                const { kind, target } = resolved;
                if (kind === 'object' && target) {
                    field.children = buildSchemaTree(target, components, new Set(seen));
                }
                else if (kind === 'array-object' && target) {
                    field.children = buildSchemaTree(target, components, new Set(seen));
                }
                else if (kind === 'ref-object' && target) {
                    field.children = buildSchemaTree(target, components, new Set(seen.add(refNameOf(field.type))));
                }
            }
        }
    }
    return flat;
}
function refNameOf(t) { return t.split('<').pop().replace(/>.*/, ''); }
function resolveFieldSchema(fieldName, parentSchema, components) {
    // Expand parent schema ($ref + allOf) to gather properties
    const visited = new Set();
    const propertyDefs = {};
    const collect = (sch) => {
        if (!sch)
            return;
        if (sch.$ref) {
            const refName = sch.$ref.split('/').pop();
            if (visited.has(refName))
                return;
            visited.add(refName);
            const target = components[refName];
            if (target)
                collect(target);
            return;
        }
        if (sch.allOf)
            for (const part of sch.allOf)
                collect(part);
        if (sch.properties)
            Object.assign(propertyDefs, sch.properties);
    };
    collect(parentSchema);
    let propSchema = propertyDefs[fieldName];
    if (!propSchema)
        return null;
    if (propSchema.$ref) {
        const refName = propSchema.$ref.split('/').pop();
        const target = components[refName];
        if (target && (target.type === 'object' || target.properties || target.allOf))
            return { kind: 'ref-object', target };
        return { kind: 'ref' };
    }
    if (propSchema.type === 'object' || propSchema.properties || propSchema.allOf)
        return { kind: 'object', target: propSchema };
    if (propSchema.type === 'array' && propSchema.items) {
        const it = propSchema.items;
        if (it.$ref) {
            const refName = it.$ref.split('/').pop();
            const target = components[refName];
            if (target && (target.type === 'object' || target.properties || target.allOf))
                return { kind: 'array-object', target };
        }
        else if (it.type === 'object' || it.properties || it.allOf) {
            return { kind: 'array-object', target: it };
        }
    }
    return null;
}
function flatten(schema, components, seen = new Set()) {
    const { required, optional } = flattenInternal(schema, components, seen);
    return { required, optional };
}
function flattenInternal(schema, components, seen = new Set()) {
    if (schema.$ref) {
        const ref = schema.$ref.split('/').pop();
        if (seen.has(ref))
            return { required: [], optional: [] };
        seen.add(ref);
        const target = components[ref];
        if (!target)
            return { required: [], optional: [] };
        return flattenInternal(target, components, seen);
    }
    let reqFields = [];
    let optFields = [];
    // Expand allOf & nested refs into a flat list of concrete schema objects
    const expand = (sch, acc, refSeen) => {
        if (!sch)
            return;
        if (sch.$ref) {
            const name = sch.$ref.split('/').pop();
            if (refSeen.has(name))
                return; // avoid cycles
            refSeen.add(name);
            const target = components[name];
            if (target)
                expand(target, acc, refSeen);
            return;
        }
        if (sch.allOf) {
            for (const part of sch.allOf)
                expand(part, acc, refSeen);
        }
        acc.push(sch);
    };
    const directSchemas = [];
    expand(schema, directSchemas, new Set());
    const collectedRequired = new Set();
    const collectedProps = {};
    for (const s of directSchemas) {
        for (const r of (s.required || []))
            collectedRequired.add(r);
        if (s.properties)
            Object.assign(collectedProps, s.properties);
    }
    for (const [prop, propSchema] of Object.entries(collectedProps)) {
        const metadata = deriveFieldMetadata(propSchema, components);
        const rawType = describeType(propSchema, components);
        const spec = {
            name: prop,
            type: normalizeType(rawType),
            ...metadata,
        };
        if (collectedRequired.has(prop))
            reqFields.push(spec);
        else
            optFields.push(spec);
    }
    // de-duplicate name-wise
    const dedupe = (arr) => {
        const seenNames = new Set();
        const out = [];
        for (const f of arr)
            if (!seenNames.has(f.name)) {
                out.push(f);
                seenNames.add(f.name);
            }
        return out.sort((a, b) => a.name.localeCompare(b.name));
    };
    reqFields = dedupe(reqFields);
    optFields = dedupe(optFields.filter(f => !reqFields.some(r => r.name === f.name)));
    return { required: reqFields, optional: optFields };
}
function describeType(schema, components, stack = []) {
    if (!schema)
        return 'unknown';
    // Direct $ref
    if (schema.$ref) {
        const ref = schema.$ref.split('/').pop();
        const target = components[ref];
        if (!target)
            return ref;
        // If referenced schema is a primitive, show primitive, else keep the name
        const prim = primitiveFromSchema(target, components, [...stack, ref]);
        return prim ?? ref;
    }
    // Arrays
    if (schema.type === 'array')
        return `array<${normalizeType(describeType(schema.items, components, stack))}>`;
    // allOf may wrap a primitive (e.g., constraints layered onto a key) -> attempt primitive extraction
    if (schema.allOf) {
        const primCandidates = schema.allOf
            .map((s) => primitiveFromSchema(s, components, stack))
            .filter((t) => !!t && t !== 'object');
        const unique = [...new Set(primCandidates)];
        if (unique.length === 1)
            return unique[0]; // unified primitive
        // fall back: if any non-primitive objects present, treat as object
        const hasObject = schema.allOf.some((s) => isObjectLike(s));
        if (hasObject)
            return 'object';
    }
    if (schema.type === 'object' || schema.properties)
        return 'object';
    if (schema.type) {
        if (schema.type === 'string')
            return 'string'; // ignore string formats
        return schema.format ? `${schema.type}(${schema.format})` : schema.type;
    }
    if (schema.oneOf)
        return schema.oneOf.map((s) => describeType(s, components, stack)).join('|');
    if (schema.anyOf)
        return schema.anyOf.map((s) => describeType(s, components, stack)).join('|');
    return 'unknown';
}
function deriveFieldMetadata(schema, components) {
    // Capture enum values and detect scalar wrapper references.
    const meta = {};
    const collectEnum = (sch) => {
        if (!sch)
            return undefined;
        if (Array.isArray(sch.enum)) {
            return sch.enum.map((v) => String(v));
        }
        if (sch.allOf) {
            // merge enums across allOf parts (rare but possible)
            const enums = sch.allOf
                .map((p) => collectEnum(p))
                .filter((e) => !!e);
            if (enums.length)
                return [...new Set(enums.flat())];
        }
        return undefined;
    };
    const detectWrapper = (refName, target) => {
        if (!target)
            return {};
        const enumValues = collectEnum(target);
        // Determine if target is effectively a primitive (possibly with allOf layering) and has no own properties
        const underlyingPrim = primitiveFromSchema(target, components, [refName]);
        const isPrimitiveLike = !!underlyingPrim && !(target.properties || target.type === 'object');
        return {
            underlying: underlyingPrim ? normalizeType(underlyingPrim) : undefined,
            wrapper: !!underlyingPrim && isPrimitiveLike,
            enumValues,
        };
    };
    // Direct property schema cases
    if (schema.$ref) {
        const ref = schema.$ref.split('/').pop();
        const target = components[ref];
        const { underlying, wrapper, enumValues } = detectWrapper(ref, target);
        if (enumValues && enumValues.length)
            meta.enumValues = enumValues;
        if (wrapper && underlying)
            meta.underlyingPrimitive = underlying;
        meta.rawRefName = ref;
        if (wrapper)
            meta.wrapper = true;
        return meta;
    }
    if (schema.allOf) {
        const enumValues = collectEnum(schema);
        if (enumValues && enumValues.length)
            meta.enumValues = enumValues;
    }
    else if (schema.enum) {
        meta.enumValues = schema.enum.map((v) => String(v));
    }
    return meta;
}
function isObjectLike(s) {
    if (!s)
        return false;
    if (s.$ref) {
        const ref = s.$ref.split('/').pop();
        return false; // we treat refs separately when resolving primitive
    }
    return !!(s.type === 'object' || s.properties || s.allOf);
}
function primitiveFromSchema(schema, components, stack) {
    if (!schema)
        return null;
    if (schema.$ref) {
        const ref = schema.$ref.split('/').pop();
        if (stack.includes(ref))
            return ref; // cycle guard returns ref name
        const target = components[ref];
        if (!target)
            return ref;
        // Recurse; if target is primitive return primitive else ref name
        const rec = primitiveFromSchema(target, components, [...stack, ref]);
        return rec ?? ref;
    }
    if (schema.allOf) {
        const parts = schema.allOf.map((p) => primitiveFromSchema(p, components, stack)).filter(Boolean);
        const uniq = [...new Set(parts)];
        if (uniq.length === 1)
            return uniq[0];
        // If all share same base (portion before '(' ), choose the most specific (longest string)
        const bases = [...new Set(uniq.map(u => u.split('(')[0]))];
        if (bases.length === 1) {
            // prefer one that has a format annotation (contains '(')
            const withFormat = uniq.filter(u => u.includes('('));
            if (withFormat.length === 1)
                return withFormat[0];
            // fallback to the longest candidate
            return uniq.sort((a, b) => b.length - a.length)[0];
        }
        return null;
    }
    if (schema.type && schema.type !== 'object' && !schema.properties && !schema.items) {
        if (schema.type === 'string')
            return 'string';
        return schema.format ? `${schema.type}(${schema.format})` : schema.type;
    }
    return null;
}
function normalizeType(t) {
    if (!t)
        return t;
    if (t.startsWith('string('))
        return 'string';
    // normalize inside array generics: array<string(foo)> => array<string>
    t = t.replace(/array<string\([^>]+\)>/g, 'array<string>');
    // collapse integer(int32|int64|...) to number
    if (t === 'integer' || /^integer\(/.test(t))
        t = 'number';
    // replace any standalone integer(...) occurrences in unions or generics
    t = t.replace(/\binteger(?:\([^)]*\))?\b/g, 'number');
    // array<integer(...)> => array<number>
    t = t.replace(/array<integer(?:\([^>]*\))?>/g, 'array<number>');
    return t;
}
function main() {
    const { workdir, commit, specContent } = sparseCheckoutSpec();
    try {
        const doc = yaml.load(specContent);
        const responses = extractResponses(doc);
        // Prune empty children objects
        for (const entry of responses) {
            pruneSchema(entry.schema);
        }
        const sha256 = crypto.createHash('sha256').update(specContent).digest('hex');
        const out = {
            metadata: {
                sourceRepo: REPO,
                commit,
                generatedAt: new Date().toISOString(),
                specPath: SPEC_PATH,
                specSha256: sha256,
            },
            responses,
        };
        const outDir = process.env.OUTPUT_DIR || 'output';
        mkdirSync(outDir, { recursive: true });
        writeFileSync(join(outDir, 'responses.json'), JSON.stringify(out, null, 2));
        // Print minimal summary for CI logs
        console.log(`Extracted ${responses.length} response schemas from commit ${commit}`);
    }
    finally {
        if (process.env.PRESERVE_SPEC_CHECKOUT) {
            console.log(`Preserving temporary spec checkout at ${workdir}`);
        }
        else {
            try {
                rmSync(workdir, { recursive: true, force: true });
            }
            catch (e) {
                console.warn(`Failed to remove temp directory ${workdir}:`, e.message);
            }
        }
    }
}
function pruneSchema(schema) {
    const visit = (sch) => {
        for (const group of [sch.required, sch.optional]) {
            for (const field of group) {
                if (field.children) {
                    visit(field.children);
                    if (field.children.required.length === 0 &&
                        field.children.optional.length === 0) {
                        delete field.children;
                    }
                }
            }
        }
    };
    visit(schema);
}
// Determine if this file is the program entry point in Node ESM
const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
    main();
}
//# sourceMappingURL=index.js.map