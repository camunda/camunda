/**
 * Read the operations and request/response properties out of one bundled
 * spec. Moved unchanged from build-bundler-version-map.mjs so that the
 * qualified-name format (`Branch.foo`, `oneOf[0].foo`, `page|Tag.limit`,
 * `Outer|Inner.foo`), which both collapse passes group by, is testable.
 */

export const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'];


/**
 * Resolve a $ref pointer like "#/components/schemas/Foo" within the spec.
 */
export function resolveRef(spec, ref, visited = new Set()) {
  if (!ref || !ref.startsWith('#/')) return null;
  if (visited.has(ref)) return null;
  visited.add(ref);
  const parts = ref.slice(2).split('/').map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~'));
  let current = spec;
  for (const p of parts) {
    if (!current || typeof current !== 'object') return null;
    // Follow intermediate $refs. The bundler produces refs like
    // `#/paths/.../foo/properties/bar` whose traversal passes through a
    // node that is itself `{ $ref: "#/components/schemas/X" }` — without
    // dereferencing those boundaries we'd return null.
    if (current.$ref && typeof current.$ref === 'string') {
      const next = resolveRef(spec, current.$ref, visited);
      if (!next) return null;
      current = next;
    }
    current = current[p];
  }
  return current || null;
}

/**
 * Resolve a schema, following $ref and unwrapping single-element allOf.
 */
export function resolveSchema(spec, schema) {
  if (!schema) return null;
  if (schema.$ref) {
    return resolveSchema(spec, resolveRef(spec, schema.$ref));
  }
  if (schema.allOf && schema.allOf.length === 1) {
    return resolveSchema(spec, schema.allOf[0]);
  }
  return schema;
}

/**
 * Convert a $ref string like "#/components/schemas/Foo" to a path array
 * like ["components", "schemas", "Foo"]. Decodes RFC 6901 JSON Pointer
 * escapes (`~1` -> `/`, `~0` -> `~`) which appear in bundler-generated
 * refs into `#/paths/...` segments.
 */
export function refToPath(ref) {
  if (!ref || !ref.startsWith('#/')) return null;
  return ref.slice(2).split('/').map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~'));
}

/**
 * Extract property names from a schema along with their depth and schema path.
 * Handles $ref, allOf (any length), and sibling properties correctly.
 *
 * Returns Map<qualifiedName, { name, depth, path }> where `qualifiedName`
 * reflects the access chain from the operation's root schema, e.g.
 *   "tenants[].name"   for a `name` field inside an array of objects
 *   "user.address.zip" for a `zip` field nested two objects deep
 * `name` is the leaf property name. The map key is the qualified name so
 * nested properties with colliding leaf names (e.g. multiple `name` fields)
 * are recorded independently rather than overwriting each other.
 */
export function extractProperties(
  spec,
  schema,
  depth = 0,
  maxDepth = 3,
  basePath = [],
  parentChain = '',
  visitedRefs = new Set(),
) {
  const props = new Map();
  if (!schema) return props;

  // Follow $ref — but merge with any sibling properties too. Guard against
  // recursive schemas (e.g. tree-shaped types) by tracking visited refs.
  if (schema.$ref) {
    if (visitedRefs.has(schema.$ref)) return props;
    const nextVisited = new Set(visitedRefs);
    nextVisited.add(schema.$ref);
    const refTarget = resolveRef(spec, schema.$ref);
    const refPath = refToPath(schema.$ref);
    const refProps = extractProperties(
      spec, refTarget, depth, maxDepth, refPath || basePath, parentChain, nextVisited,
    );
    for (const [qname, info] of refProps) {
      if (!props.has(qname)) props.set(qname, info);
    }
  }

  if (schema.properties) {
    for (const [name, propSchema] of Object.entries(schema.properties)) {
      const resolvedProp = resolveSchema(spec, propSchema);
      const type = resolvedProp?.type || (resolvedProp?.enum ? 'enum' : 'object');
      const qname = parentChain ? `${parentChain}.${name}` : name;
      if (!props.has(qname)) {
        // Property is explicitly named here — always use the inline path
        props.set(qname, { name, depth, path: [...basePath, 'properties', name] });
      }

      // Recurse into nested objects (but not too deep)
      if (depth < maxDepth && (type === 'object' || type === 'array')) {
        // The schema in front of us may be a direct $ref OR a single-element
        // `allOf: [{ $ref }]` wrapper (the Camunda convention for attaching
        // a description/x-added-in-version to a referenced schema). Both
        // forms must rebase `innerBasePath` onto the referenced schema's
        // location — otherwise nested properties get recorded under the
        // wrapper's path, which doesn't exist in the upstream YAML.
        const effectiveRef =
          propSchema.$ref
          || (Array.isArray(propSchema.allOf)
              && propSchema.allOf.length === 1
              && typeof propSchema.allOf[0]?.$ref === 'string'
              ? propSchema.allOf[0].$ref
              : null);
        let innerSchema = effectiveRef ? resolveRef(spec, effectiveRef) : resolvedProp;
        let innerBasePath = effectiveRef
          ? (refToPath(effectiveRef) || [...basePath, 'properties', name])
          : [...basePath, 'properties', name];
        let childChain = qname;
        let childVisited = visitedRefs;
        if (effectiveRef) {
          if (visitedRefs.has(effectiveRef)) continue;
          childVisited = new Set(visitedRefs);
          childVisited.add(effectiveRef);
        }
        if (type === 'array') {
          const arraySchema = effectiveRef ? resolveRef(spec, effectiveRef) : resolvedProp;
          innerSchema = arraySchema?.items || resolvedProp?.items;
          // Items may themselves be a $ref or an `allOf: [{ $ref }]` wrapper.
          const itemsRef =
            innerSchema?.$ref
            || (Array.isArray(innerSchema?.allOf)
                && innerSchema.allOf.length === 1
                && typeof innerSchema.allOf[0]?.$ref === 'string'
                ? innerSchema.allOf[0].$ref
                : null);
          if (itemsRef) {
            if (childVisited.has(itemsRef)) continue;
            childVisited = new Set(childVisited);
            childVisited.add(itemsRef);
            innerBasePath = refToPath(itemsRef) || innerBasePath;
            innerSchema = resolveRef(spec, itemsRef) || innerSchema;
          } else {
            innerBasePath = [...innerBasePath, 'items'];
          }
          childChain = `${qname}[]`;
        }
        if (!innerSchema) continue;
        const nested = extractProperties(
          spec, innerSchema, depth + 1, maxDepth, innerBasePath, childChain, childVisited,
        );
        for (const [nestedQname, nestedInfo] of nested) {
          if (!props.has(nestedQname)) {
            props.set(nestedQname, nestedInfo);
          }
        }
      }
    }
  }

  // allOf composition — handle any length (including 1 with sibling properties)
  if (schema.allOf) {
    for (let i = 0; i < schema.allOf.length; i++) {
      const sub = schema.allOf[i];
      const subBasePath = sub.$ref ? (refToPath(sub.$ref) || basePath) : [...basePath, 'allOf', String(i)];
      const subProps = extractProperties(
        spec, sub, depth, maxDepth, subBasePath, parentChain, visitedRefs,
      );
      for (const [qname, info] of subProps) {
        if (!props.has(qname)) {
          props.set(qname, info);
        }
      }
    }
  }

  // oneOf / anyOf — merge properties from all branches.
  //
  // Unlike `allOf` (which is intersection/composition: any property name
  // legitimately lives in exactly one physical schema along the chain),
  // `oneOf`/`anyOf` are independent alternative shapes. Sibling branches
  // routinely declare the same leaf property name (the discriminator
  // pattern in particular), and each branch is its own schema in the YAML
  // that needs its own `x-properties-added-in-version` annotation.
  //
  // Without disambiguation, first-wins on `qualifiedName` would silently
  // drop every branch after the first and the downstream writer would
  // annotate only one variant. Tag the qualified name with the branch's
  // referenced schema name (or the index, for inline branches) so each
  // branch gets its own entry with its own path.
  for (const keyword of ['oneOf', 'anyOf']) {
    if (!schema[keyword]) continue;
    for (let i = 0; i < schema[keyword].length; i++) {
      const sub = schema[keyword][i];
      const subBasePath = sub.$ref
        ? (refToPath(sub.$ref) || basePath)
        : [...basePath, keyword, String(i)];
      const branchTag = sub.$ref
        ? sub.$ref.split('/').pop()
        : `${keyword}[${i}]`;
      const branchChain = parentChain
        ? `${parentChain}|${branchTag}`
        : branchTag;
      const subProps = extractProperties(
        spec, sub, depth, maxDepth, subBasePath, branchChain, visitedRefs,
      );
      for (const [qname, info] of subProps) {
        if (!props.has(qname)) {
          props.set(qname, info);
        }
      }
    }
  }

  return props;
}

/**
 * Extract request body properties for an operation.
 */
function getRequestProperties(spec, operation, operationBasePath, upstreamRequestRef) {
  const body = operation.requestBody;
  if (!body) return new Map();
  const contentType = body.content?.['application/json'] ? 'application/json' : 'multipart/form-data';
  const content = body.content?.[contentType];
  if (!content?.schema) return new Map();
  // Use the bundled spec's $ref first; fall back to the upstream ref when
  // the bundler inlined the schema (losing the original $ref).
  const ref = content.schema.$ref || upstreamRequestRef;
  const schemaBasePath = ref
    ? (refToPath(ref) || [...operationBasePath, 'requestBody', 'content', contentType, 'schema'])
    : [...operationBasePath, 'requestBody', 'content', contentType, 'schema'];
  return extractProperties(spec, content.schema, 0, 3, schemaBasePath);
}

/**
 * Extract response properties for an operation (from the success response).
 */
function getResponseProperties(spec, operation, operationBasePath, upstreamResponseRef) {
  const responses = operation.responses;
  if (!responses) return new Map();

  const successCode = Object.keys(responses).find(
    (code) => code === '200' || code === '201' || code === '204'
  ) || Object.keys(responses).find((code) => code.startsWith('2'));

  if (!successCode) return new Map();
  const response = responses[successCode];
  const content = response?.content?.['application/json'];
  if (!content?.schema) return new Map();
  // Use the bundled spec's $ref first; fall back to the upstream ref when
  // the bundler inlined the schema (losing the original $ref).
  const ref = content.schema.$ref || upstreamResponseRef;
  const schemaBasePath = ref
    ? (refToPath(ref) || [...operationBasePath, 'responses', successCode, 'content', 'application/json', 'schema'])
    : [...operationBasePath, 'responses', successCode, 'content', 'application/json', 'schema'];
  return extractProperties(spec, content.schema, 0, 3, schemaBasePath);
}

/**
 * Given a path like ["components", "schemas", "Foo", ...], look up Foo's
 * source file in schemaFileMap. Returns the filename or null.
 */
export function getSchemaFileForPath(pathArr, schemaFileMap) {
  if (!schemaFileMap || !pathArr) return null;
  if (pathArr[0] === 'components' && pathArr[1] === 'schemas' && pathArr[2]) {
    return schemaFileMap.get(pathArr[2]) || null;
  }
  return null;
}

/**
 * Extract all operations and their properties from a spec object.
 * When schemaFileMap and operationFileMap are provided, cross-file property
 * paths are prefixed with the source filename.
 * operationSchemaRefMap: Map<opKey, { requestRef, responseRef }> — original
 * $ref strings from the upstream YAML files, used as fallback when the
 * bundler inlined schemas.
 */
export function extractSpecData(spec, operationFileMap, schemaFileMap, operationSchemaRefMap) {
  const operations = new Map();
  const properties = new Map();

  if (!spec?.paths) return { operations, properties };

  for (const [path, pathItem] of Object.entries(spec.paths)) {
    for (const method of HTTP_METHODS) {
      const operation = pathItem[method];
      if (!operation) continue;

      const opKey = `${method.toUpperCase()} ${path}`;
      const opSourceFile = operationFileMap?.get(opKey) || null;
      const operationBasePath = opSourceFile
        ? [opSourceFile, 'paths', path, method]
        : ['paths', path, method];
      operations.set(opKey, {
        summary: operation.summary || operation.description || '',
        operationId: operation.operationId || '',
        path: operationBasePath,
      });

      const upstreamRefs = operationSchemaRefMap?.get(opKey);
      const reqProps = getRequestProperties(spec, operation, operationBasePath, upstreamRefs?.requestRef);
      for (const [qualifiedName, propInfo] of reqProps) {
        const propKey = `${opKey} > request > ${qualifiedName}`;
        const schemaFile = getSchemaFileForPath(propInfo.path, schemaFileMap);
        const finalPath = (schemaFile && schemaFile !== opSourceFile)
          ? [schemaFile, ...propInfo.path]
          : propInfo.path;
        properties.set(propKey, {
          location: 'request',
          endpoint: opKey,
          property: propInfo.name,
          qualifiedName,
          depth: propInfo.depth,
          path: finalPath,
        });
      }

      const resProps = getResponseProperties(spec, operation, operationBasePath, upstreamRefs?.responseRef);
      for (const [qualifiedName, propInfo] of resProps) {
        const propKey = `${opKey} > response > ${qualifiedName}`;
        const schemaFile = getSchemaFileForPath(propInfo.path, schemaFileMap);
        const finalPath = (schemaFile && schemaFile !== opSourceFile)
          ? [schemaFile, ...propInfo.path]
          : propInfo.path;
        properties.set(propKey, {
          location: 'response',
          endpoint: opKey,
          property: propInfo.name,
          qualifiedName,
          depth: propInfo.depth,
          path: finalPath,
        });
      }
    }
  }

  return { operations, properties };
}
