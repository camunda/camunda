import { normalizeType, dedupeFields } from './type-utils.js';
// Functions here are extracted from original single-file implementation for clarity and testability.
export function flatten(schema, components, seen = new Set()) {
    const { required, optional } = flattenInternal(schema, components, seen);
    return { required, optional };
}
export function flattenInternal(schema, components, seen = new Set()) {
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
    const expand = (sch, acc, refSeen) => {
        if (!sch)
            return;
        if (sch.$ref) {
            const name = sch.$ref.split('/').pop();
            if (refSeen.has(name))
                return;
            refSeen.add(name);
            const target = components[name];
            if (target)
                expand(target, acc, refSeen);
            return;
        }
        if (sch.allOf)
            for (const part of sch.allOf)
                expand(part, acc, refSeen);
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
    reqFields = dedupeFields(reqFields);
    optFields = dedupeFields(optFields.filter(f => !reqFields.some(r => r.name === f.name)));
    return { required: reqFields, optional: optFields };
}
export function describeType(schema, components, stack = []) {
    if (!schema)
        return 'unknown';
    if (schema.$ref) {
        const ref = schema.$ref.split('/').pop();
        const target = components[ref];
        if (!target)
            return ref;
        const prim = primitiveFromSchema(target, components, [...stack, ref]);
        return prim ?? ref;
    }
    if (schema.type === 'array')
        return `array<${normalizeType(describeType(schema.items, components, stack))}>`;
    if (schema.allOf) {
        const primCandidates = schema.allOf
            .map((s) => primitiveFromSchema(s, components, stack))
            .filter((t) => !!t && t !== 'object');
        const unique = [...new Set(primCandidates)];
        if (unique.length === 1)
            return unique[0];
        const hasObject = schema.allOf.some((s) => isObjectLike(s));
        if (hasObject)
            return 'object';
    }
    if (schema.type === 'object' || schema.properties)
        return 'object';
    if (schema.type) {
        if (schema.type === 'string')
            return 'string';
        return schema.format ? `${schema.type}(${schema.format})` : schema.type;
    }
    if (schema.oneOf) {
        const branches = schema.oneOf.map((s) => normalizeType(describeType(s, components, stack)));
        return branches.join('|');
    }
    if (schema.anyOf) {
        const branches = schema.anyOf.map((s) => normalizeType(describeType(s, components, stack)));
        return branches.join('|');
    }
    return 'unknown';
}
export function deriveFieldMetadata(schema, components) {
    const meta = {};
    const collectEnum = (sch) => {
        if (!sch)
            return undefined;
        if (Array.isArray(sch.enum))
            return sch.enum.map((v) => String(v));
        if (sch.allOf) {
            const enums = sch.allOf
                .map((p) => collectEnum(p))
                .filter((e) => Array.isArray(e));
            if (enums.length)
                return [...new Set(enums.flat())];
        }
        return undefined;
    };
    const detectWrapper = (refName, target) => {
        if (!target)
            return {};
        const enumValues = collectEnum(target);
        const underlyingPrim = primitiveFromSchema(target, components, [refName]);
        const isPrimitiveLike = !!underlyingPrim && !(target.properties || target.type === 'object');
        return {
            underlying: underlyingPrim ? normalizeType(underlyingPrim) : undefined,
            wrapper: !!underlyingPrim && isPrimitiveLike,
            enumValues,
        };
    };
    if (schema.$ref) {
        const ref = schema.$ref.split('/').pop();
        const target = components[ref];
        const { underlying, wrapper, enumValues } = detectWrapper(ref, target);
        if (enumValues?.length)
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
        if (enumValues?.length)
            meta.enumValues = enumValues;
    }
    else if (schema.enum) {
        meta.enumValues = schema.enum.map((v) => String(v));
    }
    return meta;
}
export function isObjectLike(s) {
    if (!s)
        return false;
    if (s.$ref)
        return false; // handled elsewhere
    return !!(s.type === 'object' || s.properties || s.allOf);
}
export function primitiveFromSchema(schema, components, stack) {
    if (!schema)
        return null;
    if (schema.$ref) {
        const ref = schema.$ref.split('/').pop();
        if (stack.includes(ref))
            return ref; // cycle guard returns ref name
        const target = components[ref];
        if (!target)
            return ref;
        const rec = primitiveFromSchema(target, components, [...stack, ref]);
        return rec ?? ref;
    }
    if (schema.allOf) {
        const parts = schema.allOf.map((p) => primitiveFromSchema(p, components, stack)).filter(Boolean);
        const uniq = [...new Set(parts)];
        if (uniq.length === 1)
            return uniq[0];
        const bases = [...new Set(uniq.map(u => u.split('(')[0]))];
        if (bases.length === 1) {
            const withFormat = uniq.filter(u => u.includes('('));
            if (withFormat.length === 1)
                return withFormat[0];
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
//# sourceMappingURL=schema-flatten.js.map